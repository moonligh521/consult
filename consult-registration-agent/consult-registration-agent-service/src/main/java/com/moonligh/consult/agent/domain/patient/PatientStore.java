package com.moonligh.consult.agent.domain.patient;

import java.util.List;

/**
 * 就诊人档案。一个账号可以有多位，按账号隔离。
 */
public interface PatientStore {

    /** 该账号下未删除的就诊人。默认的排前面。 */
    List<PatientRecord> list(String accountId);

    /** 按账号和编号取一位。找不到或不是这个账号的返回 null。 */
    PatientRecord find(String accountId, String patientId);

    /** 写入一位新就诊人。 */
    PatientRecord insert(PatientRecord patient);

    /** 该账号是不是还没有就诊人。用来决定第一个是不是默认。 */
    boolean isEmpty(String accountId);
}
