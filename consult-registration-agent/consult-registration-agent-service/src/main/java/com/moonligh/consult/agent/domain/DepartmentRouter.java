package com.moonligh.consult.agent.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.DepartmentSpecialty;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;

/**
 * 按目录关键词推荐二级挂号科。一级只是分类。
 * 明确中医诉求优先；儿科按年龄或儿童标记后在儿科二级里再分；其余谁先出现跟谁。
 * 对不上时落到全科。不能覆盖急症分流的结论。
 */
public class DepartmentRouter {

    private static final Pattern AGE = Pattern.compile("(\\d{1,3})岁");

    private final DepartmentCatalog catalog;

    public DepartmentRouter() {
        this(MemoryDepartmentCatalog.seed());
    }

    public DepartmentRouter(DepartmentCatalog catalog) {
        this.catalog = catalog;
    }

    /** 推荐科室，并带上命中的关键词。年龄、性别从描述里抽。 */
    public Advice advise(String description) {
        return advise(description, null, null);
    }

    /** 推荐二级挂号科。会话里已有的年龄、性别优先于从描述再抽一次。 */
    public Advice advise(String description, Integer age, Gender gender) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        if (description == null || description.isBlank()) {
            return fallbackAdvice(snapshot);
        }
        if (SpokenNormalizer.uncertainDepartment(description, snapshot)) {
            return fallbackAdvice(snapshot);
        }
        String haystack = SpokenNormalizer.forMatch(description, snapshot);
        Hit override = firstOverride(haystack, snapshot, false);
        if (override != null) {
            Hit inner = firstRouting(haystack, snapshot, snapshot.recordOf(override.department.getCode()), null);
            return clamp(snapshot, inner != null ? inner : override);
        }
        Integer resolvedAge = age != null ? age : parseAge(haystack);
        Gender resolvedGender = gender != null ? gender : parseGender(haystack);
        Hit pediatric = pediatricHit(haystack, snapshot, resolvedAge, resolvedGender);
        if (pediatric != null) {
            return clamp(snapshot, pediatric);
        }
        Hit best = firstRouting(haystack, snapshot, null, resolvedGender);
        if (best == null) {
            return fallbackAdvice(snapshot);
        }
        return clamp(snapshot, best);
    }

    private static Advice fallbackAdvice(DepartmentSnapshot snapshot) {
        DepartmentRecord fallback = snapshot.fallback();
        return new Advice(fallback.toDepartment(), fallback.defaultSpecialty(), null);
    }

    private static Advice clamp(DepartmentSnapshot snapshot, Hit hit) {
        if (!snapshot.isEnabled(hit.department.getCode())) {
            return fallbackAdvice(snapshot);
        }
        DepartmentRecord rec = snapshot.recordOf(hit.department.getCode());
        String specialty = rec != null && rec.hasSpecialty(hit.specialty)
                ? hit.specialty
                : rec == null ? snapshot.fallback().defaultSpecialty() : rec.defaultSpecialty();
        return new Advice(hit.department, specialty, hit.keyword);
    }

    private static Hit firstOverride(String haystack, DepartmentSnapshot snapshot, boolean pediatricOnly) {
        Hit best = null;
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (dept.pediatricCatch() != pediatricOnly) {
                continue;
            }
            for (String keyword : dept.overrideKeywords()) {
                int at = haystack.indexOf(keyword);
                if (at < 0) {
                    continue;
                }
                if (best == null || at < best.at || (at == best.at && keyword.length() > best.keyword.length())) {
                    best = new Hit(dept.toDepartment(), dept.defaultSpecialty(), at, keyword);
                }
            }
        }
        return best;
    }

    private static Hit pediatricHit(String haystack, DepartmentSnapshot snapshot, Integer age, Gender gender) {
        DepartmentRecord pediatric = null;
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (dept.pediatricCatch()) {
                pediatric = dept;
                break;
            }
        }
        if (pediatric == null) {
            return null;
        }
        boolean child = false;
        Hit marker = firstOverride(haystack, snapshot, true);
        if (marker != null) {
            child = true;
        }
        if (age != null && age < pediatric.childAgeLt()) {
            child = true;
        }
        if (!child) {
            return null;
        }
        Hit routing = firstRouting(haystack, snapshot, pediatric, gender);
        if (routing != null) {
            return routing;
        }
        Hit adult = firstRouting(haystack, snapshot, null, gender);
        if (adult != null) {
            return new Hit(
                    pediatric.toDepartment(),
                    mapToPediatric(pediatric, adult.specialty()),
                    adult.at,
                    adult.keyword);
        }
        if (marker != null) {
            return marker;
        }
        return new Hit(pediatric.toDepartment(), pediatric.defaultSpecialty(), 0, "儿童");
    }

    private static Hit firstRouting(
            String haystack,
            DepartmentSnapshot snapshot,
            DepartmentRecord only,
            Gender gender) {
        Hit best = null;
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (only != null && dept.code() != only.code()) {
                continue;
            }
            if (only == null && skipByGender(dept, gender)) {
                continue;
            }
            Set<String> used = new HashSet<>();
            for (DepartmentSpecialty spec : dept.specialties()) {
                for (String keyword : spec.matchTerms()) {
                    used.add(keyword);
                    Hit hit = consider(best, haystack, dept, spec.name(), keyword);
                    if (hit != null) {
                        best = hit;
                    }
                }
            }
            String fallbackSpec = dept.defaultSpecialty();
            for (String keyword : dept.routingKeywords()) {
                if (used.contains(keyword)) {
                    continue;
                }
                Hit hit = consider(best, haystack, dept, fallbackSpec, keyword);
                if (hit != null) {
                    best = hit;
                }
            }
            if (!used.contains(dept.name()) && dept.name().length() >= 2) {
                Hit hit = consider(best, haystack, dept, fallbackSpec, dept.name());
                if (hit != null) {
                    best = hit;
                }
            }
        }
        return best;
    }

    private static Hit consider(
            Hit best,
            String haystack,
            DepartmentRecord dept,
            String specialty,
            String keyword) {
        int at = haystack.indexOf(keyword);
        if (at < 0) {
            return null;
        }
        if (best == null || at < best.at || (at == best.at && keyword.length() > best.keyword.length())) {
            return new Hit(dept.toDepartment(), specialty, at, keyword);
        }
        return null;
    }

    /** 儿科拦截后，把成人二级对到儿科二级，对不上就落儿科。 */
    private static String mapToPediatric(DepartmentRecord pediatric, String adultSpecialty) {
        if (pediatric.hasSpecialty(adultSpecialty)) {
            return adultSpecialty;
        }
        if (adultSpecialty == null || adultSpecialty.isBlank()) {
            return pediatric.defaultSpecialty();
        }
        String best = pediatric.defaultSpecialty();
        int bestLen = 0;
        for (DepartmentSpecialty spec : pediatric.specialties()) {
            String stem = spec.name().replace("小儿", "");
            if (stem.endsWith("科") && stem.length() > 2) {
                stem = stem.substring(0, stem.length() - 1);
            }
            if (stem.length() < 2) {
                continue;
            }
            if (adultSpecialty.contains(stem) && stem.length() > bestLen) {
                best = spec.name();
                bestLen = stem.length();
            }
        }
        return best;
    }

    private static boolean skipByGender(DepartmentRecord dept, Gender gender) {
        if (gender == Gender.FEMALE && dept.maleOnly()) {
            return true;
        }
        if (gender == Gender.MALE && dept.femaleOnly()) {
            return true;
        }
        return false;
    }

    private static Integer parseAge(String haystack) {
        Matcher matcher = AGE.matcher(haystack);
        if (!matcher.find()) {
            return null;
        }
        int value = Integer.parseInt(matcher.group(1));
        if (value < 1 || value > 120) {
            return null;
        }
        return value;
    }

    private static Gender parseGender(String haystack) {
        int maleAt = haystack.indexOf('男');
        int femaleAt = haystack.indexOf('女');
        if (maleAt < 0 && femaleAt < 0) {
            return null;
        }
        if (maleAt < 0) {
            return Gender.FEMALE;
        }
        if (femaleAt < 0) {
            return Gender.MALE;
        }
        return maleAt < femaleAt ? Gender.MALE : Gender.FEMALE;
    }

    /**
     * 科室建议。department 是一级类，specialty 是真正挂号的二级科。
     * evidence 是命中的关键词，没有则为空。
     */
    public record Advice(Department department, String specialty, String evidence) {
        public Advice(Department department, String evidence) {
            this(department, null, evidence);
        }
    }

    private record Hit(Department department, String specialty, int at, String keyword) {
    }
}
