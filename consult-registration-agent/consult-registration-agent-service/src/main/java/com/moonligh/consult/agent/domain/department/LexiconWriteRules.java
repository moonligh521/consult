package com.moonligh.consult.agent.domain.department;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.moonligh.consult.agent.domain.CompactText;
import com.moonligh.consult.agent.domain.safety.RuleStatus;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyRule;

/**
 * 词表写入校验。匹配层纠错不能把急症 DRAFT 影子升成基线词。
 */
public class LexiconWriteRules {

    private static final Set<String> ONE_CHAR_ALLOWED = Set.of("咳", "喘");
    private static final Pattern SAFE_PATTERN = Pattern.compile(
            "^\\(?(血压|高压|低压|血糖|尿酸|体温|心率)(\\|(血压|高压|低压|血糖|尿酸|体温|心率))*\\)?[\\[\\]\\(\\)\\|\\\\d\\-0-9\\.{},?]+$");

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

    /**
     * 同义规则写入校验：目标必须是目录病名；每组至少一个词、每词至少两字；
     * 正则只允许「血压/高压/低压/血糖/尿酸/体温/心率」后接数字类写法。
     */
    public CanonSynonym requireSynonym(CanonSynonym rule, DepartmentSnapshot snapshot) {
        if (rule == null || rule.ruleId() == null || rule.ruleId().isBlank()) {
            throw new IllegalArgumentException("同义规则编号不能为空");
        }
        if (snapshot == null || !snapshot.isDisease(rule.targetTerm())) {
            throw new IllegalArgumentException("同义规则目标必须是目录病名: " + rule.targetTerm());
        }
        if (rule.allGroups().isEmpty()) {
            throw new IllegalArgumentException("同义规则至少要有一组词");
        }
        List<List<String>> groups = new ArrayList<>();
        for (List<String> group : rule.allGroups()) {
            if (group.isEmpty()) {
                throw new IllegalArgumentException("同义规则每组至少一个词");
            }
            List<String> compacted = new ArrayList<>();
            for (String word : group) {
                String compact = requireCompact(word, "同义词");
                if (compact.length() < 2) {
                    throw new IllegalArgumentException("同义词至少两字: " + compact);
                }
                compacted.add(compact);
            }
            groups.add(compacted);
        }
        List<String> none = new ArrayList<>();
        for (String word : rule.noneTerms()) {
            none.add(requireCompact(word, "排除词"));
        }
        if (rule.pattern() != null) {
            if (!SAFE_PATTERN.matcher(rule.pattern()).matches()) {
                throw new IllegalArgumentException("正则只允许指标加数字写法: " + rule.pattern());
            }
            try {
                Pattern.compile(rule.pattern());
            } catch (PatternSyntaxException error) {
                throw new IllegalArgumentException("正则写错了: " + rule.pattern());
            }
        }
        return new CanonSynonym(rule.ruleId().trim(), rule.targetTerm(), groups, none, rule.pattern(), SynonymStatus.DRAFT);
    }

    /** 审核必须写审核人。 */
    public String requireReviewer(String reviewer) {
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("审核人不能为空");
        }
        return reviewer.trim();
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
