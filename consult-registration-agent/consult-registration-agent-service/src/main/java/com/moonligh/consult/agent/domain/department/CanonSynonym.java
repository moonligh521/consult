package com.moonligh.consult.agent.domain.department;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 口语到标准病名的归约规则。allGroups 每组至少命中一个词，所有组都命中才算；
 * noneTerms 出现任一即不算；pattern 非空时还要匹配上。匹配串是紧凑原话。
 */
public record CanonSynonym(
        String ruleId,
        String targetTerm,
        List<List<String>> allGroups,
        List<String> noneTerms,
        String pattern,
        SynonymStatus status) {

    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    public CanonSynonym {
        List<List<String>> groups = new ArrayList<>();
        if (allGroups != null) {
            for (List<String> group : allGroups) {
                groups.add(group == null ? List.of() : List.copyOf(group));
            }
        }
        allGroups = List.copyOf(groups);
        noneTerms = noneTerms == null ? List.of() : List.copyOf(noneTerms);
        pattern = pattern == null || pattern.isBlank() ? null : pattern;
        status = status == null ? SynonymStatus.DRAFT : status;
    }

    /** 命中时返回证据在紧凑串里的位置，否则空。 */
    public Hit match(String compact) {
        if (compact == null || compact.isEmpty() || allGroups.isEmpty()) {
            return null;
        }
        for (String none : noneTerms) {
            if (!none.isEmpty() && compact.contains(none)) {
                return null;
            }
        }
        int start = Integer.MAX_VALUE;
        int end = -1;
        for (List<String> group : allGroups) {
            int at = -1;
            String word = null;
            for (String item : group) {
                int found = item.isEmpty() ? -1 : compact.indexOf(item);
                if (found >= 0 && (at < 0 || found < at)) {
                    at = found;
                    word = item;
                }
            }
            if (at < 0) {
                return null;
            }
            if (at < start) {
                start = at;
                end = at + word.length();
            }
        }
        if (pattern != null) {
            Matcher matcher = COMPILED.computeIfAbsent(pattern, Pattern::compile).matcher(compact);
            if (!matcher.find()) {
                return null;
            }
            start = matcher.start();
            end = matcher.end();
        }
        return new Hit(start, end);
    }

    /** 紧凑串里的命中位置。 */
    public record Hit(int start, int end) {
    }
}
