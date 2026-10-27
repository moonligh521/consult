package com.moonligh.consult.agent.infrastructure.doctor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.config.IdentityProperties;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AuthTokens;

/**
 * 支付后向身份服务占坑派单。
 */
@Repository
public class HttpDoctorAssigner implements DoctorAssigner {

    private final RestClient client;
    private final IdentityProperties properties;

    public HttpDoctorAssigner(RestClient identityRestClient, IdentityProperties properties) {
        this.client = identityRestClient;
        this.properties = properties;
    }

    @Override
    public AssignedDoctorRes assign(
            String consultationId,
            String orderId,
            ConsultationCardRes card,
            List<String> canonicalTerms) {
        return assign(consultationId, orderId, card, canonicalTerms, 15);
    }

    @Override
    public AssignedDoctorRes assign(
            String consultationId,
            String orderId,
            ConsultationCardRes card,
            List<String> canonicalTerms,
            int consultMinutes) {
        if (card == null) {
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("departmentCode", card.department() == null ? 0 : card.department());
        payload.put("specialty", card.specialty() == null ? "" : card.specialty());
        payload.put("complaint", card.description() == null ? "" : card.description());
        payload.put("symptoms", card.symptoms() == null ? List.of() : card.symptoms());
        payload.put("canonicalTerms", canonicalTerms == null ? List.of() : canonicalTerms);
        payload.put("consultationId", consultationId);
        payload.put("orderId", orderId);
        payload.put("consultMinutes", consultMinutes);
        return postAssign(payload);
    }

    @Override
    public AssignedDoctorRes assign(
            String consultationId,
            String orderId,
            ConsultationCardRes card,
            List<String> canonicalTerms,
            int consultMinutes,
            int packageCode,
            boolean allowUpgrade) {
        if (card == null) {
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("departmentCode", card.department() == null ? 0 : card.department());
        payload.put("specialty", card.specialty() == null ? "" : card.specialty());
        payload.put("complaint", card.description() == null ? "" : card.description());
        payload.put("symptoms", card.symptoms() == null ? List.of() : card.symptoms());
        payload.put("canonicalTerms", canonicalTerms == null ? List.of() : canonicalTerms);
        payload.put("consultationId", consultationId);
        payload.put("orderId", orderId);
        payload.put("consultMinutes", consultMinutes);
        payload.put("packageCode", packageCode);
        payload.put("allowUpgrade", allowUpgrade);
        return postAssign(payload);
    }

    private AssignedDoctorRes postAssign(Map<String, Object> payload) {
        try {
            Result<AssignedDoctorRes> body = client.post()
                    .uri("/internal/doctors/assign")
                    .header(AuthTokens.INTERNAL_HEADER, properties.getInternalToken())
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<AssignedDoctorRes>>() {});
            if (body == null || !body.isSuccess() || body.getData() == null) {
                throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
            }
            return body.getData();
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            // identity 连不上时统一当成没有医生，不把内部错误抛给患者
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
    }

    @Override
    public void release(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("reason", reason == null || reason.isBlank() ? "ENDED" : reason);
        try {
            client.post()
                    .uri("/internal/doctors/release")
                    .header(AuthTokens.INTERNAL_HEADER, properties.getInternalToken())
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Void>>() {});
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
    }

    @Override
    public void extend(String orderId, int consultMinutes) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("consultMinutes", consultMinutes);
        try {
            client.post()
                    .uri("/internal/doctors/extend")
                    .header(AuthTokens.INTERNAL_HEADER, properties.getInternalToken())
                    .body(payload)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Void>>() {});
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
    }
}
