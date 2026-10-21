package com.moonligh.consult.agent.domain.safety;

import java.time.Instant;
import java.util.List;

/**
 * 用每条规则的正例、反例跑规则引擎。失败则目录不能启用。
 */
public final class CatalogSelfTest {

    private CatalogSelfTest() {
    }

    public static void run(SafetyCatalog catalog) {
        RuleSafetyEngine engine = new RuleSafetyEngine();
        Demographics demographics = new Demographics(null, null);
        for (SafetyRule rule : catalog.allRules()) {
            if (rule.status() == RuleStatus.RETIRED) {
                continue;
            }
            for (String example : rule.positiveCases()) {
                SafetyAssessment assessment = engine.evaluate(single(example), demographics, catalog, true);
                if (!assessment.matchedRuleIds().contains(rule.ruleId())) {
                    throw new CatalogException("正例未触发规则 " + rule.ruleId());
                }
            }
            for (String example : rule.negativeCases()) {
                SafetyAssessment assessment = engine.evaluate(single(example), demographics, catalog, true);
                if (assessment.matchedRuleIds().contains(rule.ruleId())) {
                    throw new CatalogException("反例触发了规则 " + rule.ruleId());
                }
            }
        }
    }

    private static List<ConsultationMessage> single(String text) {
        return List.of(new ConsultationMessage(1, "self-test", MessageSource.MESSAGE, text, Instant.EPOCH));
    }
}
