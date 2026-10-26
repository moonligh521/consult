package com.moonligh.consult.agent.domain.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.safety.ModelStatus;

class TriageEngineTest {

    private final MemoryDepartmentCatalog catalog = MemoryDepartmentCatalog.seed();
    private final DepartmentRouter router = new DepartmentRouter(catalog);

    private TriageEngine engine(ComplaintModel model) {
        return new TriageEngine(catalog, router, model);
    }

    private static ComplaintReply reply(List<ComplaintReply.ModelTerm> terms, String specialty) {
        return new ComplaintReply(ModelStatus.SUCCESS, terms, specialty, null, 12, false, "test", "stub");
    }

    private static TriageInput in(String text) {
        return new TriageInput(text, 45, Gender.MALE);
    }

    @Test
    void ruleCertainDoesNotCallModel() {
        AtomicInteger calls = new AtomicInteger();
        TriageRun run = engine((d, a, g, t, c) -> {
            calls.incrementAndGet();
            return reply(List.of(), null);
        }).run(in("咳嗽三天"));
        assertEquals(0, calls.get());
        assertEquals(DecisionSource.RULE, run.result().route().source());
        assertEquals("呼吸内科", run.result().route().advice().specialty());
        assertEquals(ModelStatus.SKIPPED, run.result().modelStatus());
        assertEquals(2, run.decisions().size());
        assertEquals(TriageNode.N4_CANON, run.decisions().get(0).node());
        assertEquals(TriageNode.N6_DEPARTMENT, run.decisions().get(1).node());
    }

    @Test
    void modelTermIsRoutedByRules() {
        TriageRun run = engine((d, a, g, t, c) -> reply(
                List.of(new ComplaintReply.ModelTerm("高血压", "头晕")), null)).run(in("头晕乏力一周"));
        TriageResult result = run.result();
        assertEquals(DecisionSource.MODEL, result.canon().source());
        assertEquals(List.of("高血压"), result.canonicalTerms());
        assertEquals(DecisionSource.MODEL, result.route().source());
        assertEquals(RouteDecision.MODEL_TERM_ROUTED, result.route().reason());
        assertEquals("普内科", result.route().advice().specialty());
    }

    @Test
    void modelTimeoutWithAmbiguousOnlyFallsBackToGeneral() {
        TriageResult result = engine((d, a, g, t, c) -> ComplaintReply.failed(ModelStatus.TIMEOUT, 2000, "test", "stub"))
                .run(in("头晕乏力一周")).result();
        assertFalse(result.canon().resolved());
        assertEquals(DecisionSource.FALLBACK, result.canon().source());
        assertEquals(Department.GENERAL, result.route().advice().department());
        assertEquals(DecisionSource.FALLBACK, result.route().source());
        assertEquals("MODEL_TIMEOUT", result.route().reason());
    }

    @Test
    void termOutsideAllowListDiscardsWholeReply() {
        TriageResult result = engine((d, a, g, t, c) -> reply(
                List.of(new ComplaintReply.ModelTerm("脑瘤", "头晕")), "神经内科")).run(in("头晕乏力一周")).result();
        assertEquals(ModelStatus.INVALID_OUTPUT, result.modelStatus());
        assertTrue(result.canonicalTerms().isEmpty());
        assertEquals(Department.GENERAL, result.route().advice().department());
    }

    @Test
    void evidenceMustBeSubstring() {
        TriageResult result = engine((d, a, g, t, c) -> reply(
                List.of(new ComplaintReply.ModelTerm("高血压", "血压很高")), null)).run(in("头晕乏力一周")).result();
        assertEquals(ModelStatus.INVALID_OUTPUT, result.modelStatus());
        assertEquals(Department.GENERAL, result.route().advice().department());
    }

    @Test
    void conflictModelPicksCandidate() {
        TriageResult result = engine((d, a, g, t, c) -> reply(List.of(), "消化内科")).run(in("胃痛，牙疼，三天")).result();
        assertEquals(RouteDecision.MODEL_PICK, result.route().reason());
        assertEquals("消化内科", result.route().advice().specialty());
    }

    @Test
    void conflictModelOutsideCandidatesFallsBack() {
        TriageResult result = engine((d, a, g, t, c) -> reply(List.of(), "眼科")).run(in("胃痛，牙疼，三天")).result();
        assertEquals(RouteDecision.MODEL_OUT_OF_CANDIDATES, result.route().reason());
        assertEquals(Department.GENERAL, result.route().advice().department());
    }

    @Test
    void fixedRouteSkipsModelForAmbiguousCanon() {
        AtomicInteger calls = new AtomicInteger();
        TriageResult result = engine((d, a, g, t, c) -> {
            calls.incrementAndGet();
            return reply(List.of(), null);
        }).run(in("想看中医调理失眠")).result();
        assertEquals(0, calls.get());
        assertEquals(TriageEngine.SKIPPED_BY_ROUTE, result.canon().reason());
        assertEquals(RouteDecision.OVERRIDE, result.route().reason());
    }

    @Test
    void modelSeesOnlyCatalogDiseasesAndCandidates() {
        TriageEngine engine = engine((d, a, g, allow, candidates) -> {
            assertTrue(allow.contains("高血压"));
            assertFalse(allow.contains("头晕"));
            assertTrue(candidates.contains("全科"));
            return reply(List.of(), null);
        });
        engine.run(in("头晕乏力一周"));
    }

    @Test
    void shortPinyinCallsModelWithOriginalEvidence() {
        AtomicInteger calls = new AtomicInteger();
        TriageRun run = engine((d, a, g, t, c) -> {
            calls.incrementAndGet();
            assertEquals("kesou", d);
            return reply(List.of(new ComplaintReply.ModelTerm("哮喘", "kesou")), "呼吸内科");
        }).run(in("kesou"));
        assertEquals(1, calls.get());
        assertEquals(List.of("哮喘"), run.result().canonicalTerms());
        assertEquals(DecisionSource.MODEL, run.result().canon().source());
        assertEquals("呼吸内科", run.result().route().advice().specialty());
    }

    @Test
    void fingerprintIgnoresSpacing() {
        assertEquals(TriageEngine.fingerprint("头晕 乏力", 45, Gender.MALE),
                TriageEngine.fingerprint("头晕乏力", 50, Gender.MALE));
    }
}
