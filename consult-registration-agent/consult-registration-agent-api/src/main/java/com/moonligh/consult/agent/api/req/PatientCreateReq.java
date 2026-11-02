package com.moonligh.consult.agent.api.req;

import com.moonligh.consult.agent.api.enums.Gender;

/**
 * 账号下新建一位就诊人。
 */
public record PatientCreateReq(
        /** 真实姓名。 */
        String name,
        /** 性别，JSON 传 1 或 2。 */
        Gender gender,
        /** 年龄，1 到 120。 */
        Integer age,
        /** 与账号持有人关系。本人/配偶/子女/父母/其他。空则记其他。 */
        String relation) {
}
