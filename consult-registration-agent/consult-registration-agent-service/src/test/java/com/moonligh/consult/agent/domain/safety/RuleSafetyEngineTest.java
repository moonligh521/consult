package com.moonligh.consult.agent.domain.safety;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 规则引擎：双重否定走不确定，「又痛了」要把前面症状拉回当前。 */
class RuleSafetyEngineTest {

    @Test
    void sf22DoubleNegationIsUndeterminedAndLaterPainEscalates() {
        SafetyCatalog catalog = CatalogLoader.load("/safety/ambiguity-catalog.json", DraftMode.SHADOW);
        RuleSafetyEngine engine = new RuleSafetyEngine();
        SafetyAssessment unclear = engine.evaluate(List.of(message(1, "不是没有胸痛")), new Demographics(null, null), catalog, true);
        assertTrue(unclear.undetermined());
        assertFalse(unclear.urgent());

        SafetyAssessment current = engine.evaluate(
                List.of(message(1, "刚才没有胸痛"), message(2, "现在开始痛了")),
                new Demographics(null, null),
                catalog,
                true);
        assertTrue(current.urgent());
    }

    private ConsultationMessage message(int seq, String text) {
        return new ConsultationMessage(seq, "m" + seq, MessageSource.MESSAGE, text, Instant.EPOCH);
    }
}
