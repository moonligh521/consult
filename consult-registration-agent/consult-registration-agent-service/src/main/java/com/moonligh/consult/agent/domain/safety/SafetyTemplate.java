package com.moonligh.consult.agent.domain.safety;

/**
 * 患者提示模板。
 */
public record SafetyTemplate(
        /** 模板编号，例如 URGENT-DEFAULT。 */
        String id,
        /** 给患者看的正文。 */
        String text,
        /** 模板本身的审核状态。 */
        RuleStatus reviewStatus) {
}
