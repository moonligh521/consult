package com.moonligh.consult.agent.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 从原句抽取性别、年龄、病程。抽不到返回 null。
 */
@Component
public class SlotParser {

    private static final Pattern AGE = Pattern.compile("(\\d{1,3})岁");
    private static final Pattern DURATION =
            Pattern.compile("(\\d+|[一二三四五六七八九十两半]+)(小时|天|周|个月|月|年)");

    /** 同时出现「男」「女」时取先出现的。 */
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

    public boolean validDuration(String value) {
        if (value == null) {
            return false;
        }
        return DURATION.matcher(value).matches();
    }
}
