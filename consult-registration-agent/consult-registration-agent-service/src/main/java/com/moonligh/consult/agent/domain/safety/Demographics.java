package com.moonligh.consult.agent.domain.safety;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 已经抽到的人口学信息。未知时按规则的 missingContextPolicy，不默认成人。
 */
public record Demographics(Integer age, Gender gender) {
}
