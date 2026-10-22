package com.moonligh.consult.agent.domain.department;

import java.util.Set;

import com.moonligh.consult.agent.domain.CompactText;
import com.moonligh.consult.agent.domain.safety.RuleStatus;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyRule;

/**
 * 词表写入校验。匹配层纠错不能把急症 DRAFT 影子升成基线词。
 */
public class LexiconWriteRules {

    private static final Set<String> ONE_CHAR_ALLOWED = Set.of("咳", "喘");

    private final SafetyCatalog safetyCatalog;

    public LexiconWriteRules(SafetyCatalog safetyCatalog) {
        this.safetyCatalog = safetyCatalog;
    }

    public String requireKeyword(String keyword) {
        String compact = requireCompact(keyword, "关键词");
        if (compact.length() == 1 && !ONE_CHAR_ALLOWED.contains(compact)) {
            throw new IllegalArgumentException("关键词过短，单字只允许咳/喘: " + compact);
        }
        return compact;
    }

    public MatchTypo requireTypo(String from, String to) {
        String compactFrom = requireCompact(from, "错词");
        String compactTo = requireCompact(to, "正词");
        if (compactFrom.equals(compactTo)) {
            throw new IllegalArgumentException("错词和正词不能相同: " + compactFrom);
        }
        if (isDraftAlias(compactFrom)) {
            throw new IllegalArgumentException("错词命中急症 DRAFT 别名，拒绝写入: " + compactFrom);
        }
        return new MatchTypo(compactFrom, compactTo);
    }

    public String requireMarker(String marker) {
        return requireCompact(marker, "不确定标记");
    }

    private static String requireCompact(String value, String label) {
        String compact = CompactText.of(value);
        if (compact.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        return compact;
    }

    private boolean isDraftAlias(String compactFrom) {
        if (safetyCatalog == null) {
            return false;
        }
        for (SafetyRule rule : safetyCatalog.allRules()) {
            if (rule.status() != RuleStatus.DRAFT) {
                continue;
            }
            for (String alias : rule.aliases()) {
                if (compactFrom.equals(CompactText.of(alias))) {
                    return true;
                }
            }
        }
        return false;
    }
}
