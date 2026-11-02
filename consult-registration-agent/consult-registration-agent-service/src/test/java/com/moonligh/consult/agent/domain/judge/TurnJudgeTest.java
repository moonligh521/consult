package com.moonligh.consult.agent.domain.judge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class TurnJudgeTest {

    @Test
    void bootstrapIsFullSteadyIsSampled() {
        assertEquals(TurnJudge.Phase.BOOTSTRAP, TurnJudge.resolvePhase(null));
        assertEquals(1.0d, TurnJudge.resolveRate(TurnJudge.Phase.BOOTSTRAP, null));
        assertEquals(1.0d, TurnJudge.resolveRate(TurnJudge.Phase.RECHECK, null));
        assertEquals(0.002d, TurnJudge.resolveRate(TurnJudge.Phase.STEADY, null));
        assertEquals(0.5d, TurnJudge.resolveRate(TurnJudge.Phase.STEADY, 0.5d));
    }

    @Test
    void skipRedactedAndWrongSpan() {
        assertEquals("redacted", TurnJudge.skipReason(obs("obs-r", "agent.turn", "[redacted]", "No card", Map.of())));
        assertEquals("name", TurnJudge.skipReason(obs("x", "agent.safety", "牙疼", "ok", Map.of())));
        assertEquals("empty", TurnJudge.skipReason(obs("e", "agent.turn", " ", "No card", Map.of())));
    }

    @Test
    void vagueLoopOnlyOnVagueAsk() {
        assertEquals(List.of(TurnJudge.PROMPT_GROUNDED, TurnJudge.VAGUE_LOOP),
                TurnJudge.evaluatorNames(vague()));
        assertEquals(List.of(TurnJudge.PROMPT_GROUNDED), TurnJudge.evaluatorNames(slot()));
        assertEquals(List.of(TurnJudge.PROMPT_GROUNDED), TurnJudge.evaluatorNames(card()));
    }

    @Test
    void sampleIsDeterministic() {
        assertEquals(TurnJudge.sampleHit("obs-vague", 0.5d), TurnJudge.sampleHit("obs-vague", 0.5d));
        assertFalse(TurnJudge.sampleHit("obs-vague", 0.0d));
        assertTrue(TurnJudge.sampleHit("obs-vague", 1.0d));
    }

    @Test
    void parseScoreClampsFencedJson() {
        TurnJudge.Score high = TurnJudge.parseScore("id", "tr", "prompt_grounded",
                "note\n```json\n{\"score\": 1.7, \"reason\": \"fits\"}\n```");
        assertEquals(1.0d, high.value());
        assertEquals("fits", high.comment());
        assertEquals(0.0d, TurnJudge.parseScore("id", "tr", "vague_loop", "{\"score\": -2, \"reason\": \"bad\"}").value());
    }

    @Test
    void renderKeepsUserSpeech() {
        String rendered = TurnJudge.render("in={{input}} node={{turn_node}}",
                Map.of("input", "有点不舒服", "turn_node", "N2_VAGUE"));
        assertTrue(rendered.contains("有点不舒服"));
        assertTrue(rendered.contains("N2_VAGUE"));
    }

    @Test
    void evaluateVagueTwiceCardOnce() {
        Map<String, String> prompts = Map.of(
                TurnJudge.PROMPT_GROUNDED, "g {{input}}",
                TurnJudge.VAGUE_LOOP, "v {{input}}");
        List<TurnJudge.Score> vagueScores = TurnJudge.evaluate(vague(), prompts, prompt -> "{\"score\":0.9,\"reason\":\"ok\"}");
        assertEquals(List.of(TurnJudge.PROMPT_GROUNDED, TurnJudge.VAGUE_LOOP),
                vagueScores.stream().map(TurnJudge.Score::name).toList());
        assertEquals(List.of(TurnJudge.PROMPT_GROUNDED),
                TurnJudge.evaluate(card(), prompts, prompt -> "{\"score\":0.9,\"reason\":\"ok\"}")
                        .stream().map(TurnJudge.Score::name).toList());
    }

    public static TurnJudge.Observation vague() {
        return obs("obs-vague", "agent.turn", "有点不舒服",
                "No card. vague_ask. VAGUE_ASK. prompt: 请您描述病情或者需要解决的健康相关问题",
                Map.of("turn_node", "N2_VAGUE", "user_prompt", "请您描述病情或者需要解决的健康相关问题"));
    }

    public static TurnJudge.Observation slot() {
        return obs("obs-slot", "agent.turn", "牙疼",
                "No card. ask_patient. ASK_GENDER. prompt: 请问为哪位患者咨询",
                Map.of("turn_node", "N3_SLOT", "missing_slot", "GENDER"));
    }

    public static TurnJudge.Observation card() {
        return obs("obs-card", "agent.turn", "3天",
                "Card \"口腔科\" · RULE. complaint model skipped.",
                Map.of("turn_node", "N6_DEPARTMENT", "card_ready", true));
    }

    private static TurnJudge.Observation obs(
            String id, String name, String input, String output, Map<String, Object> metadata) {
        return new TurnJudge.Observation(id, "tr", name, input, output, metadata);
    }
}
