package com.moonligh.consult.agent.domain;

import java.util.List;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 问诊时长。默认 15 分钟，用户可在 5/10/15/20/30 里选。
 */
public final class ConsultMinutes {

    public static final int DEFAULT = 15;
    public static final List<Integer> ALLOWED = List.of(5, 10, 15, 20, 30);

    private ConsultMinutes() {
    }

    public static int of(Integer requested) {
        if (requested == null) {
            return DEFAULT;
        }
        if (!ALLOWED.contains(requested)) {
            throw new BusinessException(ErrorCode.INVALID_CONSULT_MINUTES);
        }
        return requested;
    }
}
