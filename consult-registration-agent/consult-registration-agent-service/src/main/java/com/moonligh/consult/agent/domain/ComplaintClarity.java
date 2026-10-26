package com.moonligh.consult.agent.domain;

import java.util.regex.Pattern;

import com.moonligh.consult.agent.domain.triage.ComplaintCanon;

/**
 * 判断这句话够不够当病情描述。太含糊时先追问，不进入选就诊人。
 */
public class ComplaintClarity {

    /** 描述不清晰时的追问，给前端展示示例。 */
    public static final String DETAIL_PROMPT = "请您描述病情或者需要解决的健康相关问题";

    /** 「有点不舒服」这类没有具体症状的说法。 */
    private static final Pattern VAGUE = Pattern.compile(
            "(不舒服|难受|不适|不太好|不好受|有点事|不太对|生病了|身体不适)");

    private final SymptomExtractor symptomExtractor;
    private final SlotParser slotParser;
    private final ComplaintCanon canon;

    public ComplaintClarity(SymptomExtractor symptomExtractor, SlotParser slotParser) {
        this.symptomExtractor = symptomExtractor;
        this.slotParser = slotParser;
        this.canon = new ComplaintCanon(symptomExtractor.catalog());
    }

    /** 没有可识别症状、病程或人口学信息，并且像笼统不适。 */
    public boolean isVague(String sentence) {
        if (sentence == null) {
            return true;
        }
        String text = sentence.trim();
        if (text.isEmpty()) {
            return true;
        }
        if (!symptomExtractor.extract(text).isEmpty()) {
            return false;
        }
        if (!canon.rule(text).terms().isEmpty()) {
            return false;
        }
        if (slotParser.duration(text) != null) {
            return false;
        }
        if (slotParser.gender(text) != null || slotParser.age(text) != null) {
            return false;
        }
        if (VAGUE.matcher(text).find()) {
            return text.length() <= 16;
        }
        return text.length() < 6;
    }
}
