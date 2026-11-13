package com.moonligh.consult.agent.api.res;

import java.time.Instant;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 账号下的一位就诊人。nutritionist 用 patientVersion 做写前版本校验。
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
        boolean isDefault,
        /** 资料版本；与 consult_person.updated_at 对齐。 */
        String patientVersion,
        /** 最近更新时间。 */
        Instant updatedAt) {
}
