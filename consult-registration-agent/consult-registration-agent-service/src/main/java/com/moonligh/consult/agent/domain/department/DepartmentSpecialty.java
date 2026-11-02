package com.moonligh.consult.agent.domain.department;

import java.util.ArrayList;
import java.util.List;

/**
 * 二级挂号科。咨询科室挂这里，一级只是分类。
 * diseaseTerms 是挂词里的病名子集，主诉归约只认这些。
 */
public record DepartmentSpecialty(
        Long id,
        String name,
        String treatDesc,
        boolean enabled,
        List<String> routingKeywords,
        List<String> diseaseTerms,
        int audience) {

    public static final int AUDIENCE_UNRESTRICTED = 0;
    public static final int AUDIENCE_CHILD = 1;
    public static final int AUDIENCE_ADULT = 2;

    public DepartmentSpecialty {
        name = name == null ? "" : name;
        treatDesc = treatDesc == null ? "" : treatDesc;
        routingKeywords = routingKeywords == null ? List.of() : List.copyOf(routingKeywords);
        diseaseTerms = diseaseTerms == null ? List.of() : List.copyOf(diseaseTerms);
        if (audience < AUDIENCE_UNRESTRICTED || audience > AUDIENCE_ADULT) {
            throw new IllegalArgumentException("未知适用人群: " + audience);
        }
    }

    public DepartmentSpecialty(
            Long id,
            String name,
            String treatDesc,
            boolean enabled,
            List<String> routingKeywords,
            List<String> diseaseTerms) {
        this(id, name, treatDesc, enabled, routingKeywords, diseaseTerms, inferAudience(name));
    }

    public DepartmentSpecialty(String name, String treatDesc, List<String> routingKeywords, List<String> diseaseTerms) {
        this(null, name, treatDesc, true, routingKeywords, diseaseTerms);
    }

    public DepartmentSpecialty(String name, String treatDesc, List<String> routingKeywords) {
        this(name, treatDesc, routingKeywords, List.of());
    }

    public boolean supports(boolean child) {
        return audience == AUDIENCE_UNRESTRICTED
                || audience == (child ? AUDIENCE_CHILD : AUDIENCE_ADULT);
    }

    public static int inferAudience(String specialty) {
        if ("全科".equals(specialty)) {
            return AUDIENCE_UNRESTRICTED;
        }
        if (specialty != null && (specialty.startsWith("小儿")
                || specialty.startsWith("新生儿")
                || "儿科".equals(specialty)
                || "中医儿科".equals(specialty))) {
            return AUDIENCE_CHILD;
        }
        return AUDIENCE_ADULT;
    }

    /** 有挂词用挂词，否则用二级名和治疗描述切词。 */
    public List<String> matchTerms() {
        if (!routingKeywords.isEmpty()) {
            return routingKeywords;
        }
        List<String> terms = new ArrayList<>();
        if (!name.isBlank()) {
            terms.add(name);
        }
        for (String part : treatDesc.split("\\s+")) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
        return terms;
    }
}
