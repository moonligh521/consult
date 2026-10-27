package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.DepartmentRouter;
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
import com.moonligh.consult.framework.model.ModelStatus;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.agent.domain.triage.MatchDecision;
import com.moonligh.consult.agent.domain.triage.TriageNode;

/**
 * 出卡 → 改科 → 确认 → 支付：卡片来源、逐节点记账、派单读已存病名且不调模型。
 */
class TriageFlowTest {

    private final AtomicInteger modelCalls = new AtomicInteger();
    private final List<List<String>> assignedTerms = new ArrayList<>();
    private final InMemoryConsultationRepository repository =
            new InMemoryConsultationRepository(new ConcurrentHashMap<>(), new InMemorySafetyAuditRecorder());

    private ConsultationService service(ComplaintModel model) {
        MemoryDepartmentCatalog catalog = MemoryDepartmentCatalog.seed();
        ComplaintModel counted = (d, a, g, t, c) -> {
            modelCalls.incrementAndGet();
            return model.interpret(d, a, g, t, c);
        };
        RegistrationAgent agent = new RegistrationAgent(catalog, new DepartmentRouter(catalog), counted);
        SafetyGate gate = new SafetyGate(CatalogLoader.loadDefault(DraftMode.SHADOW),
                new com.moonligh.consult.agent.domain.safety.RuleSafetyEngine(), new DisabledSignalExtractor());
        return new ConsultationService(repository, gate, new SlotParser(), new SymptomExtractor(catalog), agent,
                new SafetyMetrics(), new InquiryProperties(), new MemoryPatientStore(),
                (consultationId, orderId, card, terms) -> {
                    assignedTerms.add(terms);
                    return new AssignedDoctorRes("doc-1", "赵衡", "主治医师", "互联网医院", 1, card.specialty(),
                            "高血压", "高血压", "sch-1", "SKILL_CANON");
                });
    }

    private List<TriageNode> nodes() {
        return repository.decisions().stream().map(MatchDecision::node).collect(Collectors.toList());
    }

    @Test
    void ruleCardRecordsNodesAndPaysWithoutModel() {
        ConsultationService service = service(ComplaintModel.unavailable());
        ConsultationRes card = service.acceptMessage(null, "男，50岁，高血压，头晕三天");
        assertEquals(ConsultationStatus.CARD_READY, card.status());
        assertEquals("普内科", card.card().specialty());
        assertEquals("RULE", card.card().departmentSource());
        assertTrue(nodes().containsAll(List.of(TriageNode.N2_VAGUE, TriageNode.N3_SLOT, TriageNode.N4_CANON,
                TriageNode.N5_SYMPTOM, TriageNode.N6_DEPARTMENT)));

        service.confirm(card.consultationId());
        int before = modelCalls.get();
        ConsultationRes paid = service.placeOrder(card.consultationId(), 3);
        assertNotNull(paid.order());
        assertEquals(before, modelCalls.get());
        assertEquals(List.of("高血压"), assignedTerms.get(0));
        assertTrue(nodes().containsAll(List.of(TriageNode.N7_CARD, TriageNode.N8_DOCTOR, TriageNode.N9_OCCUPY)));
        MatchDecision assign = repository.decisions().stream()
                .filter(row -> row.node() == TriageNode.N8_DOCTOR).findFirst().orElseThrow();
        assertEquals("SKILL_CANON", assign.reason());
        assertNotNull(assign.orderId());
    }

    @Test
    void modelTermShowsAiBadgeAndReachesAssigner() {
        ConsultationService service = service((d, a, g, t, c) -> new ComplaintReply(ModelStatus.SUCCESS,
                List.of(new ComplaintReply.ModelTerm("高血压", "头晕")), null, null, 10, false, "test", "stub"));
        ConsultationRes card = service.acceptMessage(null, "男，50岁，头晕乏力一周");
        assertEquals("MODEL", card.card().departmentSource());
        assertEquals("普内科", card.card().specialty());
        service.confirm(card.consultationId());
        service.placeOrder(card.consultationId(), 3);
        assertEquals(List.of("高血压"), assignedTerms.get(0));
        assertEquals(1, modelCalls.get());
    }

    @Test
    void modelDownFallsBackToGeneral() {
        ConsultationService service = service(ComplaintModel.unavailable());
        ConsultationRes card = service.acceptMessage(null, "男，50岁，头晕乏力一周");
        assertEquals(Department.GENERAL.getCode(), card.card().department());
        assertEquals("FALLBACK", card.card().departmentSource());
    }

    @Test
    void patientDepartmentKeepsCanonTerms() {
        ConsultationService service = service(ComplaintModel.unavailable());
        String id = service.acceptMessage(null, "男，50岁，高血压，头晕三天").consultationId();
        ConsultationRes patched = service.updateCard(
                new CardPatchReq(id, null, null, null, null, null, "心血管内科"));
        assertEquals("PATIENT", patched.card().departmentSource());
        assertEquals("心血管内科", patched.card().specialty());
        service.confirm(id);
        service.placeOrder(id, 3);
        assertEquals(List.of("高血压"), assignedTerms.get(0));
        MatchDecision confirm = repository.decisions().stream()
                .filter(row -> row.node() == TriageNode.N7_CARD).findFirst().orElseThrow();
        assertEquals("PATCHED", confirm.reason());
    }
}
