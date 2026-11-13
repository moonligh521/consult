package com.moonligh.consult.identity.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 咨询档位与医生职称对应。1 普通/住院，2 极速/主治，3 三甲/副主任，4 专家/主任。
 * 本档医生都满员时先排队；排超过 1 分钟只提一档。
 */
public final class DoctorGrade {

    private DoctorGrade() {
    }

    public static int ofTitle(String title) {
        if (title == null) {
            return 2;
        }
        return switch (title.trim()) {
            case "住院医师" -> 1;
            case "主治医师" -> 2;
            case "副主任医师" -> 3;
            case "主任医师" -> 4;
            default -> 2;
        };
    }

    /**
     * 派单要试的档位。没传档位则不过滤。
     * 排队只试所买档；提档再加高一档。
     */
    public static List<Integer> searchOrder(Integer packageCode, boolean allowUpgrade) {
        if (packageCode == null || packageCode < 1 || packageCode > 4) {
            return null;
        }
        List<Integer> grades = new ArrayList<>();
        grades.add(packageCode);
        if (allowUpgrade && packageCode < 4) {
            grades.add(packageCode + 1);
        }
        return List.copyOf(grades);
    }

    public static List<DoctorMatcher.Candidate> ofGrade(List<DoctorMatcher.Candidate> all, int grade) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<DoctorMatcher.Candidate> rows = new ArrayList<>();
        for (DoctorMatcher.Candidate row : all) {
            if (ofTitle(row.title()) == grade) {
                rows.add(row);
            }
        }
        return rows;
    }
}
