package com.moonligh.consult.identity.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 按擅长命中选人，不绑死某个姓名。二级科有命中先用，没有再到一级科找更对口的。
 * 擅长与归约病名完全相等压过原话子串命中。
 */
public final class DoctorMatcher {

    /** 归约病名精确命中的基础分，保证压过任何子串命中。 */
    static final int CANON_SCORE = 1000;

    public record Candidate(
            String id,
            String name,
            String title,
            String hospital,
            int departmentCode,
            String specialty,
            String skills,
            int inFlight,
            int remainNum,
            String scheduleId,
            long idleSinceMillis,
            int concurrentLimit) {

        public Candidate(
                String id,
                String name,
                String title,
                String hospital,
                int departmentCode,
                String specialty,
                String skills,
                int inFlight,
                int remainNum,
                String scheduleId) {
            this(id, name, title, hospital, departmentCode, specialty, skills, inFlight, remainNum, scheduleId, 0L, 3);
        }

        public Candidate(
                String id,
                String name,
                String title,
                String hospital,
                int departmentCode,
                String specialty,
                String skills,
                int inFlight,
                int remainNum,
                String scheduleId,
                long idleSinceMillis) {
            this(id, name, title, hospital, departmentCode, specialty, skills,
                    inFlight, remainNum, scheduleId, idleSinceMillis, 3);
        }
    }

    public enum MatchSource {
        SKILL_CANON,
        SKILL_TEXT,
        LOAD_L2,
        LOAD_L1,
        GENERAL
    }

    public record Pick(Candidate doctor, int score, String evidence, MatchSource source) {

        public Pick(Candidate doctor, int score, String evidence) {
            this(doctor, score, evidence, null);
        }

        Pick withSource(MatchSource value) {
            return new Pick(doctor, score, evidence, value);
        }
    }

    private DoctorMatcher() {
    }

    public static Pick pick(List<Candidate> all, String specialty, Integer departmentCode, String text) {
        return pick(all, specialty, departmentCode, text, List.of());
    }

    public static Pick pick(
            List<Candidate> all,
            String specialty,
            Integer departmentCode,
            String text,
            List<String> canonTerms) {
        if (all == null || all.isEmpty()) {
            return null;
        }
        String haystack = text == null ? "" : text;
        List<String> canon = canonTerms == null ? List.of() : canonTerms;
        List<Candidate> level2 = bySpecialty(all, specialty);
        List<Candidate> level1 = departmentCode == null ? List.of() : byDepartment(all, departmentCode);
        List<Candidate> general = byDepartment(all, 4);
        if (general.isEmpty()) {
            general = bySpecialty(all, "全科");
        }
        Pick hit = bestHit(level2, haystack, canon);
        if (hit != null && hit.score() > 0) {
            return hit.withSource(hitSource(hit));
        }
        hit = bestHit(level1, haystack, canon);
        if (hit != null && hit.score() > 0) {
            return hit.withSource(hitSource(hit));
        }
        if (!level2.isEmpty()) {
            return bestHit(level2, haystack, canon).withSource(MatchSource.LOAD_L2);
        }
        if (!level1.isEmpty()) {
            return bestHit(level1, haystack, canon).withSource(MatchSource.LOAD_L1);
        }
        Pick fallback = bestHit(general, haystack, canon);
        return fallback == null ? null : fallback.withSource(MatchSource.GENERAL);
    }

    /**
     * 公平派单用的候选人。擅长命中时只留同一档命中（归约病名优先于原话），没有命中时用这一层的全部出诊医生。
     * fallback 只在没有擅长命中时有值，用来标记二级科、一级科或全科兜底。
     */
    public static Dispatch dispatch(
            List<Candidate> all,
            String specialty,
            Integer departmentCode,
            String text,
            List<String> canonTerms) {
        if (all == null || all.isEmpty()) {
            return new Dispatch(List.of(), MatchSource.GENERAL);
        }
        String haystack = text == null ? "" : text;
        List<String> canon = canonTerms == null ? List.of() : canonTerms;
        List<Candidate> level2 = bySpecialty(all, specialty);
        List<Candidate> level1 = departmentCode == null ? List.of() : byDepartment(all, departmentCode);
        List<Candidate> general = byDepartment(all, 4);
        if (general.isEmpty()) {
            general = bySpecialty(all, "全科");
        }
        if (anyPositive(level2, haystack, canon)) {
            return new Dispatch(qualify(level2, haystack, canon), null);
        }
        if (anyPositive(level1, haystack, canon)) {
            return new Dispatch(qualify(level1, haystack, canon), null);
        }
        if (!level2.isEmpty()) {
            return new Dispatch(List.copyOf(level2), MatchSource.LOAD_L2);
        }
        if (!level1.isEmpty()) {
            return new Dispatch(List.copyOf(level1), MatchSource.LOAD_L1);
        }
        return new Dispatch(List.copyOf(general), MatchSource.GENERAL);
    }

    /** 单个医生的擅长命中。没有命中时 source 为 null。 */
    public static Pick describe(Candidate row, String text, List<String> canonTerms) {
        String haystack = text == null ? "" : text;
        List<String> canon = canonTerms == null ? List.of() : canonTerms;
        Hit hit = score(row.skills(), haystack, canon);
        MatchSource source = null;
        if (hit.score() >= CANON_SCORE) {
            source = MatchSource.SKILL_CANON;
        } else if (hit.score() > 0) {
            source = MatchSource.SKILL_TEXT;
        }
        return new Pick(row, hit.score(), hit.term(), source);
    }

    public record Dispatch(List<Candidate> doctors, MatchSource fallback) {
    }

    static List<String> skillTerms(String skills) {
        if (skills == null || skills.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String part : skills.split("[、，,;；/\\s]+")) {
            String term = part.trim();
            if (term.length() >= 2) {
                terms.add(term);
            }
        }
        return terms;
    }

    private static boolean anyPositive(List<Candidate> pool, String haystack, List<String> canon) {
        for (Candidate row : pool) {
            if (score(row.skills(), haystack, canon).score() > 0) {
                return true;
            }
        }
        return false;
    }

    /** 有归约病名命中时只留这些医生，否则留下所有原话命中。 */
    private static List<Candidate> qualify(List<Candidate> pool, String haystack, List<String> canon) {
        boolean canonHit = false;
        for (Candidate row : pool) {
            if (score(row.skills(), haystack, canon).score() >= CANON_SCORE) {
                canonHit = true;
                break;
            }
        }
        List<Candidate> matched = new ArrayList<>();
        for (Candidate row : pool) {
            int value = score(row.skills(), haystack, canon).score();
            if (canonHit ? value >= CANON_SCORE : value > 0) {
                matched.add(row);
            }
        }
        return List.copyOf(matched);
    }

    private static MatchSource hitSource(Pick hit) {
        return hit.score() >= CANON_SCORE ? MatchSource.SKILL_CANON : MatchSource.SKILL_TEXT;
    }

    private static Pick bestHit(List<Candidate> pool, String haystack, List<String> canon) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        Pick best = null;
        for (Candidate row : pool) {
            Hit hit = score(row.skills(), haystack, canon);
            if (best == null
                    || hit.score() > best.score()
                    || (hit.score() == best.score() && lighter(row, best.doctor()))) {
                best = new Pick(row, hit.score(), hit.term());
            }
        }
        return best;
    }

    private static List<Candidate> bySpecialty(List<Candidate> all, String specialty) {
        if (specialty == null || specialty.isBlank()) {
            return List.of();
        }
        List<Candidate> rows = new ArrayList<>();
        for (Candidate row : all) {
            if (specialty.equals(row.specialty())) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<Candidate> byDepartment(List<Candidate> all, int departmentCode) {
        List<Candidate> rows = new ArrayList<>();
        for (Candidate row : all) {
            if (row.departmentCode() == departmentCode) {
                rows.add(row);
            }
        }
        return rows;
    }

    private static Hit score(String skills, String haystack, List<String> canon) {
        Hit best = new Hit(0, "");
        for (String term : skillTerms(skills)) {
            int value;
            if (canon.contains(term)) {
                value = CANON_SCORE + term.length();
            } else if (haystack != null && !haystack.isBlank() && haystack.contains(term)) {
                value = term.length();
            } else {
                continue;
            }
            if (value > best.score()) {
                best = new Hit(value, term);
            }
        }
        return best;
    }

    private static boolean lighter(Candidate left, Candidate right) {
        if (left.inFlight() != right.inFlight()) {
            return left.inFlight() < right.inFlight();
        }
        return left.remainNum() > right.remainNum();
    }

    private record Hit(int score, String term) {
    }
}
