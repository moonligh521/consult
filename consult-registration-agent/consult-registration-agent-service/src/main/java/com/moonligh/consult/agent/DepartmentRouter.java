package com.moonligh.consult.agent;

import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.api.enums.Department;

@Component
public class DepartmentRouter {

    public Department recommend(String description) {
        int respiratoryAt = firstIndex(description, "咳", "气促", "呼吸");
        int neurologyAt = firstIndex(description, "头痛", "头晕");
        int digestiveAt = firstIndex(description, "腹痛", "腹泻", "恶心");

        int earliest = smallestIndex(respiratoryAt, neurologyAt, digestiveAt);
        if (earliest < 0) {
            return Department.GENERAL;
        }
        if (earliest == respiratoryAt) {
            return Department.RESPIRATORY;
        }
        if (earliest == neurologyAt) {
            return Department.NEUROLOGY;
        }
        return Department.DIGESTIVE;
    }

    private int firstIndex(String text, String... keywords) {
        int best = -1;
        for (String keyword : keywords) {
            int at = text.indexOf(keyword);
            if (at >= 0 && (best < 0 || at < best)) {
                best = at;
            }
        }
        return best;
    }

    private int smallestIndex(int a, int b, int c) {
        int best = -1;
        if (a >= 0) {
            best = a;
        }
        if (b >= 0 && (best < 0 || b < best)) {
            best = b;
        }
        if (c >= 0 && (best < 0 || c < best)) {
            best = c;
        }
        return best;
    }
}
