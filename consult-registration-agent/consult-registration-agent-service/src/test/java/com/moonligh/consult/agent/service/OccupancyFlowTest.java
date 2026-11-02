package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.patient.MemoryPatientStore;
import com.moonligh.consult.agent.domain.safety.CatalogLoader;
import com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor;
import com.moonligh.consult.agent.domain.safety.DraftMode;
import com.moonligh.consult.agent.domain.safety.InMemorySafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 支付占坑后：默认 15 分钟结束、用户改时长、手动结束只释放一次。
 */
class OccupancyFlowTest {

    private final AtomicInteger lastMinutes = new AtomicInteger();
    private final List<String> releases = new ArrayList<>();
    private final List<Integer> extensions = new ArrayList<>();

    private ConsultationService service() {
        MemoryDepartmentCatalog catalog = MemoryDepartmentCatalog.seed();
        RegistrationAgent agent = new RegistrationAgent(catalog, new DepartmentRouter(catalog),
                (d, a, g, t, c) -> {
                    throw new IllegalStateException("本测试不调模型");
                });
        SafetyGate gate = new SafetyGate(CatalogLoader.loadDefault(DraftMode.SHADOW),
                new com.moonligh.consult.agent.domain.safety.RuleSafetyEngine(), new DisabledSignalExtractor());
        DoctorAssigner assigner = new DoctorAssigner() {
            @Override
            public AssignedDoctorRes assign(
                    String consultationId,
                    String orderId,
                    ConsultationCardRes card,
                    List<String> canonicalTerms) {
                return assign(consultationId, orderId, card, canonicalTerms, 15);
            }

            @Override
            public AssignedDoctorRes assign(
                    String consultationId,
                    String orderId,
                    ConsultationCardRes card,
                    List<String> canonicalTerms,
                    int consultMinutes) {
                lastMinutes.set(consultMinutes);
                return new AssignedDoctorRes("doc-1", "周宁", "主治医师", "互联网医院", 1, card.specialty(),
                        "干咳", "干咳", "sch-1", "SKILL_TEXT");
            }

            @Override
            public void release(String orderId, String reason) {
                releases.add(reason);
            }

            @Override
            public void extend(String orderId, int consultMinutes) {
                extensions.add(consultMinutes);
            }
        };
        return new ConsultationService(
                new InMemoryConsultationRepository(new ConcurrentHashMap<>(), new InMemorySafetyAuditRecorder()),
                gate, new SlotParser(), new SymptomExtractor(catalog), agent,
                new SafetyMetrics(), new InquiryProperties(), new MemoryPatientStore(), assigner);
    }

    private ConsultationRes paid(ConsultationService service, Integer minutes) {
        String id = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        service.confirm(id);
        return service.placeOrder(id, 3, minutes);
    }

    @Test
    void payDefaultsToFifteenMinutes() {
        ConsultationRes view = paid(service(), null);
        assertEquals(15, lastMinutes.get());
        assertEquals(15, view.order().consultMinutes());
        assertNotNull(view.order().endsAt());
        assertTrue(Duration.between(Instant.now(), view.order().endsAt()).toMinutes() >= 14);
        assertNull(view.order().endedAt());
    }

    @Test
    void userCanSetThirtyMinutes() {
        ConsultationRes view = paid(service(), 30);
        assertEquals(30, lastMinutes.get());
        assertEquals(30, view.order().consultMinutes());
    }

    @Test
    void rejectUnknownMinutes() {
        ConsultationService service = service();
        String id = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        service.confirm(id);
        BusinessException error = assertThrows(BusinessException.class, () -> service.placeOrder(id, 3, 7));
        assertEquals(ErrorCode.INVALID_CONSULT_MINUTES, error.getErrorCode());
    }

    @Test
    void endConsultReleasesOnce() {
        ConsultationService service = service();
        String id = paid(service, 15).consultationId();
        ConsultationRes ended = service.endConsult(id, "ENDED");
        assertNotNull(ended.order().endedAt());
        assertEquals("ENDED", ended.order().endReason());
        assertEquals(List.of("ENDED"), releases);
        ConsultationRes again = service.endConsult(id, "ENDED");
        assertEquals(ended.order().endedAt(), again.order().endedAt());
        assertEquals(1, releases.size());
    }

    @Test
    void changeMinutesWhileActive() {
        ConsultationService service = service();
        String id = paid(service, 15).consultationId();
        ConsultationRes changed = service.setConsultMinutes(id, 20);
        assertEquals(20, changed.order().consultMinutes());
        assertEquals(List.of(20), extensions);
        service.endConsult(id, "ENDED");
        BusinessException error = assertThrows(BusinessException.class, () -> service.setConsultMinutes(id, 10));
        assertEquals(ErrorCode.CONSULT_ENDED, error.getErrorCode());
    }
}
