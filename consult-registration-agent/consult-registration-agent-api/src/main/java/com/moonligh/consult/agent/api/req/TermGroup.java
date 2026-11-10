package com.moonligh.consult.agent.api.req;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 同义规则里的一组词。这一组至少命中一个词才算这组通过。
 * 接口和库里的 JSON 仍是字符串数组，不改已有数据。
 */
public record TermGroup(List<String> words) {

    public TermGroup {
        words = words == null ? List.of() : List.copyOf(words);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TermGroup of(List<String> words) {
        return new TermGroup(words);
    }

    @JsonValue
    public List<String> words() {
        return words;
    }
}
