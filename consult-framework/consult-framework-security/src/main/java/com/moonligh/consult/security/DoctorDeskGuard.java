package com.moonligh.consult.security;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 接诊台只认令牌里的医生编号。请求里再带另一个编号就拒绝。没绑定的不能回复、不能心跳。
 */
public final class DoctorDeskGuard {

    private DoctorDeskGuard() {
    }

    public static String required(String boundDoctorId) {
        if (boundDoctorId == null || boundDoctorId.isBlank()) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        return boundDoctorId.trim();
    }

    public static void same(String boundDoctorId, String requestedDoctorId) {
        String bound = required(boundDoctorId);
        if (requestedDoctorId == null || !bound.equals(requestedDoctorId.trim())) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
    }
}
