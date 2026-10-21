package com.moonligh.consult.agent.domain;

import org.springframework.stereotype.Component;

/**
 * 安全分流。只拦急症和用户明确要求转人工。
 * 其余描述生成咨询卡片，交给线上医生；要不要线下看由医生说。
 */
@Component
public class SafetyGate {

    /** V0.1 四个急症短语，本版不扩展同义。 */
    private static final String[] URGENT_PHRASES = {"胸痛", "呼吸困难", "意识障碍", "口唇发紫"};

    /** 用户自己要求转人工，不自动进图文/视频。 */
    private static final String[] HUMAN_PHRASES = {"转人工", "找人工"};

    /**
     * 按优先级返回命中结果。描述为空则不算命中。
     */
    public SafetyHit classify(String description) {
        if (description == null || description.isBlank()) {
            return SafetyHit.NONE;
        }
        if (containsAny(description, URGENT_PHRASES)) {
            return SafetyHit.URGENT;
        }
        if (containsAny(description, HUMAN_PHRASES)) {
            return SafetyHit.HUMAN;
        }
        return SafetyHit.NONE;
    }

    private boolean containsAny(String description, String[] phrases) {
        for (String phrase : phrases) {
            if (description.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
