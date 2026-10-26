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
            String scheduleId) {
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
