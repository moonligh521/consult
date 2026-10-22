package com.moonligh.consult.agent.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;

/**
 * 从描述里抽出卡片可选字段：过敏、用药、伴随症状。
 */
public final class CardFacts {

    private static final Pattern ALLERGY = Pattern.compile("([^，。；,、\\s]{1,12}过敏)");
    private static final Pattern MEDICATION = Pattern.compile("(?:在吃|吃了|服用|用药)([^，。；,、\\s]{1,16})");

    private CardFacts() {
    }

    /** 组一张卡片。科室以 advice 为准。 */
    public static ConsultationCardRes compose(
            Gender gender,
            Integer age,
            String duration,
            String description,
            List<String> symptoms,
            DepartmentRouter.Advice advice) {
        List<String> tags = symptoms == null ? List.of() : symptoms;
        String allergy = first(ALLERGY, description);
        List<String> associated = new ArrayList<>();
        if (tags.size() > 1) {
            for (String tag : tags.subList(1, tags.size())) {
                if (allergy != null && (allergy.contains(tag) || tag.contains("过敏"))) {
                    continue;
                }
                associated.add(tag);
            }
        }
        return new ConsultationCardRes(
                gender,
                age,
                duration,
                description,
                tags,
                advice.department(),
                advice.specialty(),
                associated,
                allergy,
                first(MEDICATION, description),
                advice.evidence());
    }

    private static String first(Pattern pattern, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
    }
}
