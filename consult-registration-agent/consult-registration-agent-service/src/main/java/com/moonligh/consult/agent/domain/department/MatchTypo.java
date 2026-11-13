package com.moonligh.consult.agent.domain.department;

/**
 * 挂号匹配用的错词→正词。急症 DRAFT 别名不能进这张表。
 */
public record MatchTypo(String from, String to) {

    public MatchTypo {
        from = from == null ? "" : from;
        to = to == null ? "" : to;
    }
}
