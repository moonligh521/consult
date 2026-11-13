package com.moonligh.consult.agent.api.res;

import java.util.List;

/**
 * 咨询记录详情。view 是分诊结果，transcript 是诊室画面快照，lines 是原话。
 */
public record ConsultationDetailRes(
        ConsultationRes view,
        String title,
        String transcript,
        List<ConsultLineRes> lines) {

    public ConsultationDetailRes(ConsultationRes view, String title, String transcript) {
        this(view, title, transcript, List.of());
    }
}
