package com.moonligh.consult.agent.domain;

import org.springframework.stereotype.Component;

/**
 * 急症分流。命中四个短语之一就停止普通导诊，提示打 120。
 * 必须先于补槽和科室推荐。
 */
@Component
public class SafetyGate {

    /**
     * V0.1 只认这四个词，写在交付基线里。
     */
    private static final String[] PHRASES = {"胸痛", "呼吸困难", "意识障碍", "口唇发紫"};

    /**
     * 描述里出现任一短语就返回 true。
     */
    public boolean triggered(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }
        for (String phrase : PHRASES) {
            if (description.contains(phrase)) {
                return true;
            }
        }
        return false;
    }
}
