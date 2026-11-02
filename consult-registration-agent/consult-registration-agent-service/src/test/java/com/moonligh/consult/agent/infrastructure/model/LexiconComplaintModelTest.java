package com.moonligh.consult.agent.infrastructure.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.department.InformalLexicon;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.agent.domain.triage.ComplaintModel;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.agent.domain.triage.DecisionSource;
import com.moonligh.consult.agent.domain.triage.RouteDecision;
import com.moonligh.consult.agent.domain.triage.TriageEngine;
import com.moonligh.consult.agent.domain.triage.TriageInput;
import com.moonligh.consult.framework.model.ModelStatus;

/**
 * 规则 NO_HIT 后先走词表模型：拼音/英文/方言解码成目录词再挂科。
 */
class LexiconComplaintModelTest {

    private final MemoryDepartmentCatalog catalog = MemoryDepartmentCatalog.seed();
    private final DepartmentRouter router = new DepartmentRouter(catalog);
    private final LexiconComplaintModel lexicon = new LexiconComplaintModel(catalog, router, InformalLexicon.load());

    @Test
    void pinyinCoughGoesRespiratoryThroughModelSlot() {
        TriageEngine engine = new TriageEngine(catalog, router, lexicon);
        var result = engine.run(new TriageInput("kesou三天", 32, Gender.FEMALE)).result();
        assertEquals(ModelStatus.SUCCESS, result.modelStatus());
        assertEquals(DecisionSource.MODEL, result.route().source());
        assertEquals("呼吸内科", result.route().advice().specialty());
        assertEquals(RouteDecision.MODEL_PICK, result.route().reason());
    }

    @Test
    void spacedPinyinEvidenceStaysOriginalSubstring() {
        ComplaintReply reply = lexicon.interpret("ke sou，挂什么科啊", 32, Gender.FEMALE, List.of(), List.of("呼吸内科", "全科"));
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertEquals("呼吸内科", reply.specialty());
        assertTrue("ke sou，挂什么科啊".contains(reply.specialtyEvidence()));
    }

    @Test
    void asciiAliasesOnlyMatchWholeTokens() {
        InformalLexicon dictionary = InformalLexicon.load();
        assertFalse(dictionary.rewrite("shivering").hit());
        assertFalse(dictionary.rewrite("routine check").hit());
        assertFalse(dictionary.rewrite("ｓhiv").hit());
        assertFalse(dictionary.rewrite("hivｅ").hit());
    }

    @Test
    void negatedHistoricalAndFamilyComplaintsDoNotBecomeCurrentDiseases() {
        List<String> candidates = List.of("普内科", "全科");
        assertEquals(ModelStatus.UNAVAILABLE,
                lexicon.interpret("没有gaoxueya", 32, Gender.FEMALE, List.of("高血压"), candidates).status());
        assertEquals(ModelStatus.UNAVAILABLE,
                lexicon.interpret("以前gaoxueya", 32, Gender.FEMALE, List.of("高血压"), candidates).status());
        assertEquals(ModelStatus.UNAVAILABLE,
                lexicon.interpret("我妈有gaoxueya", 32, Gender.FEMALE, List.of("高血压"), candidates).status());
        assertEquals(ModelStatus.UNAVAILABLE,
                lexicon.interpret("不算gaoxueya", 32, Gender.FEMALE, List.of("高血压"), candidates).status());
        assertEquals(ModelStatus.SUCCESS,
                lexicon.interpret("gaoxueya三天", 32, Gender.FEMALE, List.of("高血压"), candidates).status());
        assertEquals(ModelStatus.SUCCESS,
                lexicon.interpret("没有发烧但有gaoxueya", 32, Gender.FEMALE, List.of("高血压"), candidates).status());
    }

    @Test
    void specialtyEvidenceComesFromTheAliasThatDroveTheRoute() {
        ComplaintReply reply = lexicon.interpret(
                "touyun，gaoxueya",
                32,
                Gender.FEMALE,
                List.of("高血压"),
                List.of("神经内科", "普内科", "全科"));
        assertEquals(ModelStatus.SUCCESS, reply.status());
        assertEquals("普内科", reply.specialty());
        assertEquals("gaoxueya", reply.specialtyEvidence());
    }

    @Test
    void englishCoughGoesRespiratory() {
        var result = new TriageEngine(catalog, router, lexicon)
                .run(new TriageInput("cough three days", 32, Gender.FEMALE)).result();
        assertEquals("呼吸内科", result.route().advice().specialty());
        assertEquals(DecisionSource.MODEL, result.route().source());
    }

    @Test
    void dialectHeadacheGoesNeurology() {
        var result = new TriageEngine(catalog, router, lexicon)
                .run(new TriageInput("脑壳痛三天", 32, Gender.FEMALE)).result();
        assertEquals("神经内科", result.route().advice().specialty());
    }

    @Test
    void unlistedTypoCoughGoesRespiratory() {
        var result = new TriageEngine(catalog, router, lexicon)
                .run(new TriageInput("克嗽三天", 32, Gender.FEMALE)).result();
        assertEquals("呼吸内科", result.route().advice().specialty());
        assertEquals(DecisionSource.MODEL, result.route().source());
    }

    @Test
    void unknownGibberishDoesNotGuess() {
        ComplaintReply reply = lexicon.interpret("asdfghjklqwerty", 32, Gender.FEMALE, List.of(), List.of("全科"));
        assertEquals(ModelStatus.UNAVAILABLE, reply.status());
    }

    @Test
    void cascadeUsesLexiconBeforeLlm() {
        AtomicInteger llmCalls = new AtomicInteger();
        ComplaintModel llm = (d, a, g, t, c) -> {
            llmCalls.incrementAndGet();
            return ComplaintReply.failed(ModelStatus.UNAVAILABLE, 0, "test", "stub");
        };
        ComplaintModel cascade = new CascadeComplaintModel(lexicon, llm);
        var result = new TriageEngine(catalog, router, cascade)
                .run(new TriageInput("kesou三天", 32, Gender.FEMALE)).result();
        assertEquals(0, llmCalls.get());
        assertEquals("呼吸内科", result.route().advice().specialty());
    }

    @Test
    void cascadeFallsThroughToLlmWhenLexiconMisses() {
        AtomicInteger llmCalls = new AtomicInteger();
        ComplaintModel llm = (d, a, g, t, c) -> {
            llmCalls.incrementAndGet();
            return ComplaintReply.failed(ModelStatus.UNAVAILABLE, 0, "test", "stub");
        };
        ComplaintReply reply = new CascadeComplaintModel(lexicon, llm)
                .interpret("asdfghjklqwerty", 32, Gender.FEMALE, List.of(), List.of("全科"));
        assertEquals(1, llmCalls.get());
        assertEquals(ModelStatus.UNAVAILABLE, reply.status());
    }
}
