package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.domain.judge.TurnJudge;
import com.moonligh.consult.agent.domain.judge.TurnJudgeTest;
import com.moonligh.consult.agent.infrastructure.judge.JudgeModelPort;
import com.moonligh.consult.agent.infrastructure.judge.LangfuseTurnPort;
import com.moonligh.consult.framework.model.ModelProperties;
import com.moonligh.consult.framework.observability.ObservabilityProperties;

public class TurnJudgeServiceTest {

    @Test
    void bootstrapScoresEligibleTurnsAndDoesNotCallConsultApi() {
        FakeLangfuse langfuse = new FakeLangfuse();
        TurnJudgeService service = service(true, "bootstrap", langfuse, prompt -> "{\"score\":0.4,\"reason\":\"ok\"}");
        TurnJudgeService.Summary summary = service.runOnce(List.of(
                TurnJudgeTest.vague(),
                TurnJudgeTest.slot(),
                TurnJudgeTest.card(),
                new TurnJudge.Observation("obs-redacted", "tr-x", "agent.turn", "[redacted]", "No card", Map.of())
        ), false);
        assertEquals("bootstrap", summary.phase);
        assertEquals(1.0d, summary.sampleRate);
        assertEquals(1, summary.skipped);
        assertEquals(3, summary.sampled);
        assertEquals(4, summary.scored);
        assertEquals(0, summary.errors);
        assertEquals(List.of(TurnJudge.PROMPT_GROUNDED, TurnJudge.VAGUE_LOOP, TurnJudge.PROMPT_GROUNDED, TurnJudge.PROMPT_GROUNDED),
                summary.scores.stream().map(TurnJudge.Score::name).toList());
        assertTrue(langfuse.posted.isEmpty());
    }

    @Test
    void applyPostsScoresNotConsultationPath() {
        FakeLangfuse langfuse = new FakeLangfuse();
        TurnJudgeService service = service(true, "bootstrap", langfuse, prompt -> "{\"score\":0.4,\"reason\":\"loop\"}");
        service.runOnce(List.of(TurnJudgeTest.vague()), true);
        assertEquals(2, langfuse.posted.size());
        assertEquals("prompt_grounded-obs-vague", langfuse.posted.get(0).scoreId());
    }

    @Test
    void judgeFailureDoesNotAbortBatch() {
        FakeLangfuse langfuse = new FakeLangfuse();
        TurnJudgeService service = service(true, "bootstrap", langfuse, prompt -> "not-json");
        TurnJudgeService.Summary summary = service.runOnce(
                List.of(TurnJudgeTest.vague(), TurnJudgeTest.card()), false);
        assertEquals(2, summary.errors);
        assertEquals(0, summary.scored);
    }

    @Test
    void disabledOrMissingKeysSkip() {
        FakeLangfuse langfuse = new FakeLangfuse();
        TurnJudgeService.Summary off = service(false, "bootstrap", langfuse, prompt -> "{\"score\":1}").runOnce();
        assertEquals(0, off.scanned);
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getJudge().setEnabled(true);
        TurnJudgeService missing = new TurnJudgeService(
                properties, new ModelProperties(), langfuse, prompt -> "{\"score\":1}");
        assertEquals(0, missing.runOnce().scanned);
    }

    @Test
    void steadyPhaseUsesSampleRate() {
        FakeLangfuse langfuse = new FakeLangfuse();
        TurnJudgeService service = service(true, "steady", langfuse, prompt -> "{\"score\":0.9,\"reason\":\"ok\"}");
        List<TurnJudge.Observation> turns = List.of(TurnJudgeTest.vague(), TurnJudgeTest.slot(), TurnJudgeTest.card());
        TurnJudgeService.Summary summary = service.runOnce(turns, false);
        long expected = turns.stream().filter(obs -> TurnJudge.sampleHit(obs.id(), 0.002d)).count();
        assertEquals("steady", summary.phase);
        assertEquals(0.002d, summary.sampleRate);
        assertEquals(expected, summary.sampled);
    }

    private static TurnJudgeService service(
            boolean enabled, String phase, FakeLangfuse langfuse, JudgeModelPort model) {
        ObservabilityProperties properties = new ObservabilityProperties();
        properties.getJudge().setEnabled(enabled);
        properties.getJudge().setPhase(phase);
        properties.getLangfuse().setPublicUrl("http://langfuse.test");
        properties.getLangfuse().setPublicKey("pk");
        properties.getLangfuse().setSecretKey("sk");
        ModelProperties models = new ModelProperties();
        models.setApiKey("k");
        return new TurnJudgeService(properties, models, langfuse, model);
    }

    private static final class FakeLangfuse implements LangfuseTurnPort {
        private final List<TurnJudge.Score> posted = new ArrayList<>();

        @Override
        public List<TurnJudge.Observation> fetchTurns(int sinceMinutes, int limit) {
            return List.of();
        }

        @Override
        public void postScore(TurnJudge.Score score) {
            posted.add(score);
        }
    }
}
