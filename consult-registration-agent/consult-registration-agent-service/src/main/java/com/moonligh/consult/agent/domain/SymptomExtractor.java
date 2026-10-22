package com.moonligh.consult.agent.domain;

import java.util.ArrayList;
import java.util.List;

import com.moonligh.consult.agent.domain.department.DepartmentCatalog;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;

/**
 * 从病情描述中抽取症状标签。短语来自匹配快照的 ROUTING 词，另补急症用的呼吸困难/胸痛。
 */
public class SymptomExtractor {

    private final DepartmentCatalog catalog;

    public SymptomExtractor() {
        this(MemoryDepartmentCatalog.seed());
    }

    public SymptomExtractor(DepartmentCatalog catalog) {
        this.catalog = catalog;
    }

    /** 从描述里抽出不重复的症状标签。 */
    public List<String> extract(String description) {
        DepartmentSnapshot snapshot = catalog.snapshot();
        List<String> found = new ArrayList<>();
        String text = SpokenNormalizer.forMatch(description, snapshot);
        int index = 0;
        while (index < text.length()) {
            String match = longestPhraseAt(text, index, snapshot.symptomPhrases());
            if (match == null) {
                index++;
                continue;
            }
            if (!found.contains(match)) {
                found.add(match);
            }
            index = index + match.length();
        }
        return List.copyOf(found);
    }

    /** 从 index 开始，找出能对上的最长短语。 */
    private static String longestPhraseAt(String description, int index, List<String> phrases) {
        String best = null;
        for (String phrase : phrases) {
            boolean startsHere = description.startsWith(phrase, index);
            if (!startsHere) {
                continue;
            }
            if (best == null || phrase.length() > best.length()) {
                best = phrase;
            }
        }
        return best;
    }
}
