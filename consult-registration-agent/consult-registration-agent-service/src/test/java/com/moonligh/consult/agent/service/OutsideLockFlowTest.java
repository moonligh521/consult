package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.agent.domain.InMemoryConsultationRepository;
import com.moonligh.consult.agent.domain.RegistrationAgent;
import com.moonligh.consult.agent.domain.SessionAction;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.patient.MemoryPatientStore;
import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor;
import com.moonligh.consult.agent.domain.safety.DraftMode;
import com.moonligh.consult.agent.domain.safety.InMemorySafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.ModelStatus;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 模型和身份服务都不能在会话锁里调：锁只陪读改写，不陪外部调用等待。
 */
class OutsideLockFlowTest {

    private final AtomicBoolean inLock = new AtomicBoolean();
    private final AtomicInteger modelCalls = new AtomicInteger();
    private final AtomicInteger callsInLock = new AtomicInteger();
    private final List<String> assignedOrderIds = new ArrayList<>();
    private final List<String> releases = new ArrayList<>();
    private boolean failAssign;

    private final InMemoryConsultationRepository repository =
            new InMemoryConsultationRepository(new ConcurrentHashMap<>(), new InMemorySafetyAuditRecorder()) {
                @Override
                public ConsultationRes runLocked(String consultationId, SessionAction action) {
                    inLock.set(true);
                    try {
                        return super.runLocked(consultationId, action);
                    } finally {
                        inLock.set(false);
                    }
                }
            };

    private ConsultationService service() {
        MemoryDepartmentCatalog catalog = MemoryDepartmentCatalog.seed();
        ComplaintModel model = (d, a, g, t, c) -> {
            modelCalls.incrementAndGet();
            if (inLock.get()) {
                callsInLock.incrementAndGet();
            }
            return new ComplaintReply(ModelStatus.SUCCESS,
                    List.of(new ComplaintReply.ModelTerm("高血压", "头晕")), null, null, 10, false, "test", "stub");
        };
        RegistrationAgent agent = new RegistrationAgent(catalog, new DepartmentRouter(catalog), model);
        SafetyGate gate = new SafetyGate(CatalogLoader.loadDefault(DraftMode.SHADOW),
                new RuleSafetyEngine(), new DisabledSignalExtractor());
        DoctorAssigner assigner = new DoctorAssigner() {
            @Override
            public AssignedDoctorRes assign(
                    String consultationId, String orderId, ConsultationCardRes card, List<String> terms) {
                if (inLock.get()) {
                    callsInLock.incrementAndGet();
                }
                assignedOrderIds.add(orderId);
                if (failAssign) {
                    throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
                }
                return new AssignedDoctorRes("doc-1", "赵衡", "主治医师", "互联网医院", 1, card.specialty(),
                        "高血压", "高血压", "sch-1", "SKILL_CANON");
            }

            @Override
            public void release(String orderId, String reason) {
                if (inLock.get()) {
                    callsInLock.incrementAndGet();
                }
                releases.add(orderId);
            }
        };
        return new ConsultationService(repository, gate, new SlotParser(), new SymptomExtractor(catalog), agent,
                new SafetyMetrics(), new InquiryProperties(), new MemoryPatientStore(), assigner);
    }

    @Test
    void complaintModelRunsOnceAndOutsideLock() {
        ConsultationRes card = service().acceptMessage(null, "男，50岁，头晕乏力一周");
        assertEquals("MODEL", card.card().departmentSource());
        assertEquals(1, modelCalls.get());
        assertEquals(0, callsInLock.get());
    }

    @Test
    void followUpMessageAlsoRunsModelOutsideLock() {
        ConsultationService service = service();
        String id = service.acceptMessage(null, "头晕乏力一周").consultationId();
        service.acceptMessage(id, "男，50岁");
        assertEquals(0, callsInLock.get());
    }

    @Test
    void assignAndReleaseHappenOutsideLock() {
        ConsultationService service = service();
        String id = service.acceptMessage(null, "男，50岁，高血压，头晕三天").consultationId();
        service.confirm(id);
        ConsultationRes paid = service.placeOrder(id, 3);
        assertNotNull(paid.order().doctor());
        service.endConsult(id, "ENDED");
        assertEquals(List.of(paid.order().orderId()), releases);
        assertEquals(0, callsInLock.get());
    }

    @Test
    void failedAssignKeepsOrderAndRetryReusesOrderId() {
        ConsultationService service = service();
        String id = service.acceptMessage(null, "男，50岁，高血压，头晕三天").consultationId();
        service.confirm(id);
        failAssign = true;
        ConsultationRes queued = service.placeOrder(id, 3);
        assertNotNull(queued.order());
        assertNull(queued.order().doctor());
        assertNotNull(repository.getRequired(id).getOrder());
        assertNull(repository.getRequired(id).getOrder().doctor());

        failAssign = false;
        ConsultationRes retried = service.placeOrder(id, 3);
        assertNotNull(retried.order().doctor());
        assertEquals(2, assignedOrderIds.size());
        assertEquals(assignedOrderIds.get(0), assignedOrderIds.get(1));
        assertEquals(retried.order().orderId(), assignedOrderIds.get(1));
        assertFalse(retried.order().orderId().isBlank());
    }
}
