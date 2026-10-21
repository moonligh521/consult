package com.moonligh.consult.agent.domain.safety;

/**
 * 患者提示模板。
 */
public record SafetyTemplate(String id, String text, RuleStatus reviewStatus) {
}
