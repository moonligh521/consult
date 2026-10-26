package com.moonligh.consult.agent.domain.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;

class DepartmentRouterRouteTest {

    private final DepartmentSnapshot seed = MemoryDepartmentCatalog.seed().snapshot();
    private final ComplaintCanon canon = new ComplaintCanon(MemoryDepartmentCatalog.seed());
    private final DepartmentRouter router = new DepartmentRouter(MemoryDepartmentCatalog.seed());

    private RouteDecision route(String text, Integer age, Gender gender, DepartmentSnapshot snapshot) {
        return router.route(new RouteInput(text, canon.rule(text, snapshot), age, gender), snapshot);
    }

    @Test
    void canonTermRoutesToItsSpecialty() {
        RouteDecision decision = route("高血压，头晕三天", 50, Gender.MALE, seed);
        assertEquals(RouteDecision.CANON_TERM, decision.reason());
        assertEquals("普内科", decision.advice().specialty());
        assertTrue(decision.certain());
    }

    @Test
    void reviewedSynonymRoutesLikeLiteral() {
        DepartmentSnapshot snapshot = ComplaintCanonTest.reviewed(seed, "SYN-DM-002");
        RouteDecision decision = route("最近老是口渴，喝水多，夜里老跑厕所", 40, Gender.FEMALE, snapshot);
        assertEquals(RouteDecision.CANON_TERM, decision.reason());
        assertEquals("内分泌科", decision.advice().specialty());
    }

    @Test
    void singleClearSymptom() {
        RouteDecision decision = route("咳嗽三天", 30, Gender.MALE, seed);
        assertEquals(RouteDecision.SINGLE_SPECIALTY, decision.reason());
        assertEquals("呼吸内科", decision.advice().specialty());
    }

    @Test
    void ambiguousSymptomIsIgnoredForRouting() {
        RouteDecision decision = route("头痛，恶心，两天", 30, Gender.MALE, seed);
        assertTrue(decision.certain());
        assertEquals("神经内科", decision.advice().specialty());
    }

    @Test
    void ambiguousOnlyIsUndecidedWithGeneralCandidate() {
        RouteDecision decision = route("头晕乏力一周", 30, Gender.MALE, seed);
        assertFalse(decision.certain());
        assertEquals(RouteDecision.AMBIGUOUS_ONLY, decision.reason());
        assertTrue(decision.candidates().contains("全科"));
        assertEquals(Department.GENERAL, decision.advice().department());
    }

    @Test
    void uncertainMarkerGoesGeneral() {
        RouteDecision decision = route("不知道挂哪个科，头晕", 30, Gender.MALE, seed);
        assertEquals(RouteDecision.UNCERTAIN_MARKER, decision.reason());
        assertEquals(Department.GENERAL, decision.advice().department());
    }

    @Test
    void tcmOverrideWins() {
        RouteDecision decision = route("想看中医调理失眠", 40, Gender.FEMALE, seed);
        assertEquals(RouteDecision.OVERRIDE, decision.reason());
        assertEquals(10, decision.advice().department().getCode());
    }

    @Test
    void pediatricUsesCanonTermAsHint() {
        DepartmentSnapshot snapshot = ComplaintCanonTest.reviewed(seed, "SYN-DM-002");
        RouteDecision decision = route("孩子口渴，喝水多，尿多", 5, Gender.MALE, snapshot);
        assertEquals(RouteDecision.PEDIATRIC, decision.reason());
        assertEquals(8, decision.advice().department().getCode());
    }

    @Test
    void crossL1SymptomsConflict() {
        RouteDecision decision = route("胃痛，牙疼，三天", 30, Gender.MALE, seed);
        assertEquals(RouteDecision.CONFLICT, decision.reason());
        assertFalse(decision.certain());
        assertTrue(decision.candidates().contains("消化内科"));
        assertTrue(decision.candidates().contains("全科"));
    }
}
