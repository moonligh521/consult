package com.moonligh.consult.agent.api.res;

/**
 * 咨询记录详情。view 是分诊结果，transcript 是诊室画面快照。
 */
public record ConsultationDetailRes(
        ConsultationRes view,
        String title,
        String transcript) {
}
