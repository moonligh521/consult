package com.moonligh.consult.agent.domain.department;

import java.util.List;

import com.moonligh.consult.agent.api.enums.Department;

/**
 * 目录里的一个科室。关键词只给已开通的科用来匹配。
 */
public record DepartmentRecord(
        int code,
        String name,
        boolean enabled,
        int sort,
        boolean fallback,
        int genderScope,
        Integer childAgeLt,
        List<String> routingKeywords,
        List<String> overrideKeywords) {

    public DepartmentRecord {
        routingKeywords = routingKeywords == null ? List.of() : List.copyOf(routingKeywords);
        overrideKeywords = overrideKeywords == null ? List.of() : List.copyOf(overrideKeywords);
    }

    public boolean maleOnly() {
        return genderScope == 1;
    }

    public boolean femaleOnly() {
        return genderScope == 2;
    }

    public boolean pediatricCatch() {
        return childAgeLt != null;
    }

    public Department toDepartment() {
        return Department.fromCode(code);
    }
}
