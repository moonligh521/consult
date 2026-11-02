package com.moonligh.consult.agent.domain.department;

import java.util.List;

import com.moonligh.consult.agent.api.enums.Department;

/**
 * 目录里的一个一级类。真正挂号挂到 specialties 里的二级科。
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
        List<String> overrideKeywords,
        String treatDesc,
        List<DepartmentSpecialty> specialties) {

    public DepartmentRecord {
        routingKeywords = routingKeywords == null ? List.of() : List.copyOf(routingKeywords);
        overrideKeywords = overrideKeywords == null ? List.of() : List.copyOf(overrideKeywords);
        treatDesc = treatDesc == null ? "" : treatDesc;
        specialties = specialties == null ? List.of() : List.copyOf(specialties);
    }

    public DepartmentRecord(
            int code,
            String name,
            boolean enabled,
            int sort,
            boolean fallback,
            int genderScope,
            Integer childAgeLt,
            List<String> routingKeywords,
            List<String> overrideKeywords) {
        this(code, name, enabled, sort, fallback, genderScope, childAgeLt,
                routingKeywords, overrideKeywords, "", List.of());
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
        return Department.tryFromCode(code);
    }

    public DepartmentRecord withSpecialties(List<DepartmentSpecialty> live) {
        return new DepartmentRecord(
                code, name, enabled, sort, fallback, genderScope, childAgeLt,
                routingKeywords, overrideKeywords, treatDesc, live);
    }

    public DepartmentRecord withRouting(List<String> routing) {
        return new DepartmentRecord(
                code, name, enabled, sort, fallback, genderScope, childAgeLt,
                routing, overrideKeywords, treatDesc, specialties);
    }

    public DepartmentRecord withoutSpecialty(String specialtyName) {
        if (specialtyName == null || specialtyName.isBlank()) {
            return this;
        }
        java.util.Set<String> dropped = new java.util.HashSet<>();
        List<DepartmentSpecialty> kept = new java.util.ArrayList<>();
        for (DepartmentSpecialty spec : specialties) {
            if (specialtyName.equals(spec.name())) {
                dropped.addAll(spec.matchTerms());
            } else {
                kept.add(spec);
            }
        }
        List<String> routing = new java.util.ArrayList<>();
        for (String keyword : routingKeywords) {
            if (!dropped.contains(keyword)) {
                routing.add(keyword);
            }
        }
        return withSpecialties(kept).withRouting(routing);
    }

    /** 没有更细的二级时，落到与一级同名的二级，否则第一个二级。 */
    public String defaultSpecialty() {
        for (DepartmentSpecialty spec : specialties) {
            if (name.equals(spec.name())) {
                return spec.name();
            }
        }
        if (!specialties.isEmpty()) {
            return specialties.get(0).name();
        }
        return name;
    }

    public boolean hasSpecialty(String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return false;
        }
        for (DepartmentSpecialty spec : specialties) {
            if (specialty.equals(spec.name())) {
                return true;
            }
        }
        return specialties.isEmpty() && name.equals(specialty);
    }
}
