package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.enums.Department;

/**
 * 按关键词推荐科室。谁在句子里先出现就跟谁。
 * 对不上时落到全科。不能覆盖急症分流的结论。
 */
public class DepartmentRouter {

    /** 根据描述里先出现的关键词推荐科室。 */
    public Department recommend(String description) {
        int respiratoryAt = firstIndex(description, "咳", "气促", "呼吸");
        int neurologyAt = firstIndex(description, "头痛", "头晕");
        int digestiveAt = firstIndex(description, "腹痛", "腹泻", "恶心");

        // 谁在句子里更靠前，就推荐哪个科室。
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

    /** 这组关键词里最早出现的位置。都没有返回 -1。 */
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

    /** 三个位置里取最小的非负数。全是 -1 说明都没命中。 */
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
