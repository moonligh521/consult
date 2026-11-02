package com.moonligh.consult.agent.domain.triage;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 一次分诊的输入。description 是卡片描述，age/gender 来自会话。
 */
public record TriageInput(String description, Integer age, Gender gender) {
}
