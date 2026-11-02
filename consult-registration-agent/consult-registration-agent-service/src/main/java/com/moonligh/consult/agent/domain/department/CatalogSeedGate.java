package com.moonligh.consult.agent.domain.department;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 种子有、库无、且二级科从未被管理端改过时，拒绝用这份目录替换上一份快照。
 * 对账看原始目录行，停用和改名不算漏同步。
 */
public final class CatalogSeedGate {

    private CatalogSeedGate() {
    }

    public static List<String> missingUntouched(
            DepartmentSnapshot seed,
            List<DepartmentRecord> database,
            Set<String> adminTouched) {
        List<String> gaps = new ArrayList<>();
        for (DepartmentRecord dept : seed.enabled()) {
            if (touchedDepartment(adminTouched, dept.code())) {
                continue;
            }
            DepartmentRecord stored = recordOf(database, dept.code());
            for (DepartmentSpecialty spec : dept.specialties()) {
                if (!spec.enabled()) {
                    continue;
                }
                if (adminTouched.contains(dept.code() + "\n" + spec.name())) {
                    continue;
                }
                DepartmentSpecialty actual = stored == null ? null : specialty(stored, spec.name());
                if (actual == null) {
                    if (touchedAnySpecialty(adminTouched, dept.code())) {
                        continue;
                    }
                    gaps.add(dept.name() + "/" + spec.name() + " 缺少二级科");
                    continue;
                }
                for (String term : spec.routingKeywords()) {
                    if (!actual.routingKeywords().contains(term)) {
                        gaps.add(dept.name() + "/" + spec.name() + " 缺少挂词 " + term);
                    }
                }
                for (String term : spec.diseaseTerms()) {
                    if (!actual.diseaseTerms().contains(term)) {
                        gaps.add(dept.name() + "/" + spec.name() + " 缺少病名 " + term);
                    }
                }
            }
        }
        return List.copyOf(gaps);
    }

    private static boolean touchedDepartment(Set<String> adminTouched, int code) {
        return adminTouched.contains(code + "\n");
    }

    private static boolean touchedAnySpecialty(Set<String> adminTouched, int code) {
        String prefix = code + "\n";
        for (String key : adminTouched) {
            if (key.startsWith(prefix) && key.length() > prefix.length()) {
                return true;
            }
        }
        return false;
    }

    private static DepartmentRecord recordOf(List<DepartmentRecord> records, int code) {
        for (DepartmentRecord rec : records) {
            if (rec.code() == code) {
                return rec;
            }
        }
        return null;
    }

    private static DepartmentSpecialty specialty(DepartmentRecord dept, String name) {
        for (DepartmentSpecialty spec : dept.specialties()) {
            if (name.equals(spec.name())) {
                return spec;
            }
        }
        return null;
    }
}
