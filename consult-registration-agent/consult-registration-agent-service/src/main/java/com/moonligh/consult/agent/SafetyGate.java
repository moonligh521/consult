package com.moonligh.consult.agent;

import org.springframework.stereotype.Component;

@Component
public class SafetyGate {

    private static final String[] PHRASES = {"胸痛", "呼吸困难", "意识障碍", "口唇发紫"};

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
