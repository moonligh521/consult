package com.moonligh.consult.agent.api.res;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 账号下的一位就诊人。
 */
public record PatientRes(
        /** 就诊人编号。 */
        String id,
        /** 真实姓名。展示时由前端脱敏。 */
        String name,
        /** 性别。 */
        Gender gender,
        /** 年龄。 */
        Integer age,
        /** 与账号持有人关系。 */
        String relation,
        /** 是否该账号默认就诊人。 */
        boolean isDefault) {
}
