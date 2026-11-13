package com.moonligh.consult.agent.domain;

import java.util.List;

import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;

/**
 * 向身份服务按二级科和擅长派医生。canonicalTerms 取自会话里已存的分诊结论，派单不调模型。测试可不接。
 */
public interface DoctorAssigner {

    AssignedDoctorRes assign(String consultationId, String orderId, ConsultationCardRes card, List<String> canonicalTerms);

    default AssignedDoctorRes assign(
            String consultationId,
            String orderId,
            ConsultationCardRes card,
            List<String> canonicalTerms,
            int consultMinutes) {
        return assign(consultationId, orderId, card, canonicalTerms);
    }

    default AssignedDoctorRes assign(
            String consultationId,
            String orderId,
            ConsultationCardRes card,
            List<String> canonicalTerms,
            int consultMinutes,
            int packageCode,
            boolean allowUpgrade) {
        return assign(consultationId, orderId, card, canonicalTerms, consultMinutes);
    }

    /** 当前时段该科有号才收款。测试默认有号。 */
    default boolean hasOnDuty(ConsultationCardRes card, int packageCode) {
        return true;
    }

    default void release(String orderId, String reason) {
    }

    default void extend(String orderId, int consultMinutes) {
    }
}
