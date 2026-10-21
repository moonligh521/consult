package com.moonligh.consult.agent.domain;

import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.api.enums.Department;

/**
 * 按关键词推荐科室。谁在句子里先出现就跟谁。
 * 对不上时落到全科。不能覆盖急症分流的结论。
 */
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

    /**
     * 一组词里最早出现的下标；都没有返回 -1。
     */
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

    /**
     * 三个下标里最小的非负数；都是 -1 则返回 -1。
     */
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
