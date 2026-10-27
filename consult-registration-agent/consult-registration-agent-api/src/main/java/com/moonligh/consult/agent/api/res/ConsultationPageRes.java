package com.moonligh.consult.agent.api.res;

import java.util.List;

/**
 * 当前账号一年内的咨询记录分页。
 */
public record ConsultationPageRes(
        long total,
        int page,
        int size,
        List<ConsultationRecordRes> items) {
}
