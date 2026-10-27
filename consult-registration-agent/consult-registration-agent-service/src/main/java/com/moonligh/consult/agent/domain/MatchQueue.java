package com.moonligh.consult.agent.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 派医生排队：同一二级科按支付时间先进先出。
 * 本档医生满员先等；队头等超过 1 分钟只提一档。
 */
public final class MatchQueue {

    public record Entry(
            String consultationId,
            String orderId,
            Instant paidAt,
            int packageCode,
            String specialty,
            int queuedGrade) {

        public Entry(String consultationId, String orderId, Instant paidAt, int packageCode, String specialty) {
            this(consultationId, orderId, paidAt, packageCode, specialty, packageCode);
        }

        public Entry promoted() {
            return new Entry(consultationId, orderId, paidAt, packageCode, specialty, Math.min(4, queuedGrade + 1));
        }
    }

    private MatchQueue() {
    }

    public static int gradeOfTitle(String title) {
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

    /** 1 普通/住院，2 极速/主治，3 三甲/副主任，4 专家/主任。 */
    public static List<Integer> grades(int packageCode, boolean allowUpgrade) {
        if (packageCode < 1 || packageCode > 4) {
            return List.of();
        }
        List<Integer> grades = new ArrayList<>();
        grades.add(packageCode);
        if (allowUpgrade && packageCode < 4) {
            grades.add(packageCode + 1);
        }
        return List.copyOf(grades);
    }

    public static boolean waitedLongEnough(Instant paidAt, Instant now, Duration upgradeAfter) {
        if (paidAt == null || now == null || upgradeAfter == null) {
            return false;
        }
        return !paidAt.plus(upgradeAfter).isAfter(now);
    }

    /**
     * 同一科里，更早支付、且跟本单抢同一档医生的人还在排队，就先不派。
     */
    public static boolean blockedByEarlier(Entry me, List<Entry> queue, Instant now, Duration upgradeAfter) {
        if (me == null || queue == null || queue.isEmpty()) {
            return false;
        }
        boolean myUpgrade = waitedLongEnough(me.paidAt(), now, upgradeAfter);
        List<Integer> mine = grades(me.packageCode(), myUpgrade);
        String specialty = me.specialty() == null ? "" : me.specialty();
        for (Entry other : queue) {
            if (other == null || me.orderId().equals(other.orderId())) {
                continue;
            }
            String theirs = other.specialty() == null ? "" : other.specialty();
            if (!specialty.equals(theirs)) {
                continue;
            }
            int cmp = compareFifo(other, me);
            if (cmp >= 0) {
                continue;
            }
            boolean theirUpgrade = waitedLongEnough(other.paidAt(), now, upgradeAfter);
            if (overlaps(mine, grades(other.packageCode(), theirUpgrade))) {
                return true;
            }
        }
        return false;
    }

    static int compareFifo(Entry left, Entry right) {
        Instant a = left.paidAt() == null ? Instant.EPOCH : left.paidAt();
        Instant b = right.paidAt() == null ? Instant.EPOCH : right.paidAt();
        int byTime = a.compareTo(b);
        if (byTime != 0) {
            return byTime;
        }
        String leftId = left.orderId() == null ? "" : left.orderId();
        String rightId = right.orderId() == null ? "" : right.orderId();
        return leftId.compareTo(rightId);
    }

    private static boolean overlaps(List<Integer> left, List<Integer> right) {
        for (Integer grade : left) {
            if (right.contains(grade)) {
                return true;
            }
        }
        return false;
    }
}
