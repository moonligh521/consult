package com.moonligh.consult.agent.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 从患者原句里抽出性别、年龄、病程。抽不到返回 null，由编排决定追问。
 */
@Component
public class SlotParser {

    /**
     * 例如「32岁」。超出 1～120 当作没抽到。
     */
    private static final Pattern AGE = Pattern.compile("(\\d{1,3})岁");
    /**
     * 例如「半年」「3天」。
     */
    private static final Pattern DURATION =
            Pattern.compile("(\\d+|[一二三四五六七八九十两半]+)(小时|天|周|个月|月|年)");

    /**
     * 句中同时有「男」「女」时，取先出现的那个。
     */
    public Gender gender(String sentence) {
        int maleAt = sentence.indexOf('男');
        int femaleAt = sentence.indexOf('女');
        if (maleAt < 0 && femaleAt < 0) {
            return null;
        }
        if (maleAt < 0) {
            return Gender.FEMALE;
        }
        if (femaleAt < 0) {
            return Gender.MALE;
        }
        if (maleAt < femaleAt) {
            return Gender.MALE;
        }
        return Gender.FEMALE;
    }

    public Integer age(String sentence) {
        Matcher matcher = AGE.matcher(sentence);
        if (!matcher.find()) {
            return null;
        }
        int value = Integer.parseInt(matcher.group(1));
        if (value < 1 || value > 120) {
            return null;
        }
        return value;
    }

    public String duration(String sentence) {
        Matcher matcher = DURATION.matcher(sentence);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group();
    }

    /**
     * 改卡片时校验病程格式。
     */
    public boolean validDuration(String value) {
        if (value == null) {
            return false;
        }
        return DURATION.matcher(value).matches();
    }
}
