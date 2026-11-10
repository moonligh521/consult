package com.moonligh.consult.agent.infrastructure.doctor;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.AssignDoctorReq;
import com.moonligh.consult.identity.api.ExtendDoctorReq;
import com.moonligh.consult.identity.api.ReleaseDoctorReq;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.consult.security.ConsultAuthProperties;

/**
 * 支付后向身份服务占坑派单。
 */
@Repository
public class HttpDoctorAssigner implements DoctorAssigner {

    private final RestClient client;
    private final ConsultAuthProperties auth;

    public HttpDoctorAssigner(RestClient identityRestClient, ConsultAuthProperties auth) {
        this.client = identityRestClient;
        this.auth = auth;
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
        return postAssign(assignRequest(consultationId, orderId, card, canonicalTerms, consultMinutes, null, null));
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
        return postAssign(assignRequest(
                consultationId, orderId, card, canonicalTerms, consultMinutes, packageCode, allowUpgrade));
    }

    private static AssignDoctorReq assignRequest(
            String consultationId,
            String orderId,
            ConsultationCardRes card,
            List<String> canonicalTerms,
            int consultMinutes,
            Integer packageCode,
            Boolean allowUpgrade) {
        return new AssignDoctorReq(
                card.department() == null ? 0 : card.department(),
                card.specialty() == null ? "" : card.specialty(),
                card.description() == null ? "" : card.description(),
                card.symptoms() == null ? List.of() : card.symptoms(),
                canonicalTerms == null ? List.of() : canonicalTerms,
                consultationId,
                orderId,
                consultMinutes,
                packageCode,
                allowUpgrade);
    }

    private AssignedDoctorRes postAssign(AssignDoctorReq request) {
        try {
            Result<AssignedDoctorRes> body = client.post()
                    .uri("/internal/doctors/assign")
                    .header(AuthTokens.INTERNAL_HEADER, auth.getInternalToken())
                    .body(request)
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
    public boolean hasOnDuty(ConsultationCardRes card, int packageCode) {
        if (card == null) {
            return false;
        }
        try {
            Result<Boolean> body = client.get()
                    .uri("/internal/doctors/available?departmentCode={code}&specialty={spec}&packageCode={pkg}",
                            card.department() == null ? 0 : card.department(),
                            card.specialty() == null ? "" : card.specialty(),
                            packageCode)
                    .header(AuthTokens.INTERNAL_HEADER, auth.getInternalToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Boolean>>() {});
            return body != null && body.isSuccess() && Boolean.TRUE.equals(body.getData());
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public void release(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        ReleaseDoctorReq request = new ReleaseDoctorReq(
                orderId, reason == null || reason.isBlank() ? "ENDED" : reason);
        try {
            client.post()
                    .uri("/internal/doctors/release")
                    .header(AuthTokens.INTERNAL_HEADER, auth.getInternalToken())
                    .body(request)
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
        try {
            client.post()
                    .uri("/internal/doctors/extend")
                    .header(AuthTokens.INTERNAL_HEADER, auth.getInternalToken())
                    .body(new ExtendDoctorReq(orderId, consultMinutes))
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<Void>>() {});
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
    }
}
