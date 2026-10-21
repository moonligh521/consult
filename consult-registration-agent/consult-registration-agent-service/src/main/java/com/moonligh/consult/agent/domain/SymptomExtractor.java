package com.moonligh.consult.agent.domain;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * 从病情描述里抽出症状标签。V0.1 用关键词，不用模型。
 * 同一位置优先匹配更长的词，避免「呼吸困难」被拆成「呼吸」。
 */
@Component
public class SymptomExtractor {

    private static final String[] PHRASES = {
            "呼吸困难", "干咳", "咳嗽", "气促", "胸痛", "头痛", "头晕", "腹痛", "腹泻", "恶心", "呼吸"
    };

    public List<String> extract(String description) {
        List<String> found = new ArrayList<>();
        int index = 0;
        while (index < description.length()) {
            String match = longestPhraseAt(description, index);
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

    /**
     * 从 index 起，找出能对上的最长短语。
     */
    private String longestPhraseAt(String description, int index) {
        String best = null;
        for (String phrase : PHRASES) {
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
