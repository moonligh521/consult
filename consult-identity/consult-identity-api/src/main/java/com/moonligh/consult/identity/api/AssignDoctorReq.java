package com.moonligh.consult.identity.api;

import java.util.List;

/**
 * 支付后按二级科和擅长派医生。canonicalTerms 是分诊归约出的病名，擅长与之完全相等优先。
 */
public record AssignDoctorReq(
        Integer departmentCode,
        String specialty,
        String complaint,
        List<String> symptoms,
        List<String> canonicalTerms,
        String consultationId,
        String orderId,
        Integer consultMinutes) {

    public AssignDoctorReq(Integer departmentCode, String specialty, String complaint, List<String> symptoms) {
        this(departmentCode, specialty, complaint, symptoms, List.of(), null, null, null);
    }
}
