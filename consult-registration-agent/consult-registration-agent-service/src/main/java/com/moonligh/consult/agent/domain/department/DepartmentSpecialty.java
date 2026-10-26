package com.moonligh.consult.agent.domain.department;

import java.util.ArrayList;
import java.util.List;

/**
 * 二级挂号科。咨询科室挂这里，一级只是分类。
 * diseaseTerms 是挂词里的病名子集，主诉归约只认这些。
 */
public record DepartmentSpecialty(
        String name,
        String treatDesc,
        List<String> routingKeywords,
        List<String> diseaseTerms) {

    public DepartmentSpecialty {
        name = name == null ? "" : name;
        treatDesc = treatDesc == null ? "" : treatDesc;
        routingKeywords = routingKeywords == null ? List.of() : List.copyOf(routingKeywords);
        diseaseTerms = diseaseTerms == null ? List.of() : List.copyOf(diseaseTerms);
    }

    public DepartmentSpecialty(String name, String treatDesc, List<String> routingKeywords) {
        this(name, treatDesc, routingKeywords, List.of());
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
