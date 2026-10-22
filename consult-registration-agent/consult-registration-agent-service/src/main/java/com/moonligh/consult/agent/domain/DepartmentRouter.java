package com.moonligh.consult.agent.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentRecord;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;

/**
 * 按目录关键词推荐科室。只落在当前已开通的科室里。
 * 明确中医诉求优先；儿科按年龄或儿童标记；其余谁先出现跟谁。
 * 对不上时落到目录兜底科。不能覆盖急症分流的结论。
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

    public Department requireEnabled(Department department) {
        return catalog.snapshot().requireEnabled(department.getCode());
    }

    /** 根据描述里先出现的关键词推荐科室。 */
    public Department recommend(String description) {
        return advise(description).department();
    }

    /** 推荐科室，并带上命中的关键词。年龄、性别从描述里抽。 */
    public Advice advise(String description) {
        return advise(description, null, null);
    }

    /** 推荐科室。会话里已有的年龄、性别优先于从描述再抽一次。 */
    public Advice advise(String description, Integer age, Gender gender) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        Department fallback = snapshot.fallbackDepartment();
        if (description == null || description.isBlank()) {
            return new Advice(fallback, null);
        }
        if (SpokenNormalizer.uncertainDepartment(description, snapshot)) {
            return new Advice(fallback, null);
        }
        String haystack = SpokenNormalizer.forMatch(description, snapshot);
        Hit override = firstOverride(haystack, snapshot, false);
        if (override != null) {
            return clamp(snapshot, override);
        }
        Integer resolvedAge = age != null ? age : parseAge(haystack);
        Gender resolvedGender = gender != null ? gender : parseGender(haystack);
        Hit pediatric = pediatricHit(haystack, snapshot, resolvedAge);
        if (pediatric != null) {
            return clamp(snapshot, pediatric);
        }
        Hit best = null;
        for (DepartmentRecord dept : snapshot.enabled()) {
            if (skipByGender(dept, resolvedGender)) {
                continue;
            }
            for (String keyword : dept.routingKeywords()) {
                int at = haystack.indexOf(keyword);
                if (at < 0) {
                    continue;
                }
                if (best == null || at < best.at || (at == best.at && keyword.length() > best.keyword.length())) {
                    best = new Hit(dept.toDepartment(), at, keyword);
                }
            }
        }
        if (best == null) {
            return new Advice(fallback, null);
        }
        return clamp(snapshot, best);
    }

    private static Advice clamp(DepartmentSnapshot snapshot, Hit hit) {
        if (!snapshot.isEnabled(hit.department.getCode())) {
            return new Advice(snapshot.fallbackDepartment(), null);
        }
        return new Advice(hit.department, hit.keyword);
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
                    best = new Hit(dept.toDepartment(), at, keyword);
                }
            }
        }
        return best;
    }

    private static Hit pediatricHit(String haystack, DepartmentSnapshot snapshot, Integer age) {
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
        Hit marker = firstOverride(haystack, snapshot, true);
        if (marker != null) {
            return marker;
        }
        if (age != null && age < pediatric.childAgeLt()) {
            return new Hit(pediatric.toDepartment(), 0, "儿童");
        }
        return null;
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

    /** 科室建议。evidence 是命中的关键词，没有则为空。 */
    public record Advice(Department department, String evidence) {
    }

    private record Hit(Department department, int at, String keyword) {
    }
}
