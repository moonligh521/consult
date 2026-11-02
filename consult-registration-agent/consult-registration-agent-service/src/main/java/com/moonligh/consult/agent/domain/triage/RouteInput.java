package com.moonligh.consult.agent.domain.triage;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * N6 挂科的输入：原描述、N4 结论、会话里已有的年龄性别。
 */
public record RouteInput(String description, CanonResult canon, Integer age, Gender gender) {
}
