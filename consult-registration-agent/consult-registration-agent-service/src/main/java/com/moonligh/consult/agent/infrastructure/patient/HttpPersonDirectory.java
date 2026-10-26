package com.moonligh.consult.agent.infrastructure.patient;

import java.util.List;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.agent.config.IdentityProperties;
import com.moonligh.consult.agent.domain.patient.PatientRecord;
import com.moonligh.consult.agent.domain.patient.PatientStore;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AuthTokens;

/**
 * 向身份服务核对就诊人归属。列表和新建不走咨询服务。
 */
@Repository
public class HttpPersonDirectory implements PatientStore {

    private final RestClient client;
    private final IdentityProperties properties;

    public HttpPersonDirectory(RestClient identityRestClient, IdentityProperties properties) {
        this.client = identityRestClient;
        this.properties = properties;
    }

    @Override
    public List<PatientRecord> list(String accountId) {
        throw new UnsupportedOperationException("就诊人列表在身份服务");
    }

    @Override
    public PatientRecord find(String accountId, String patientId) {
        if (accountId == null || accountId.isBlank() || patientId == null || patientId.isBlank()) {
            return null;
        }
        try {
            Result<PatientRes> body = client.get()
                    .uri("/internal/persons/{id}", patientId)
                    .header(AuthTokens.ACCOUNT_HEADER, accountId)
                    .header("X-Internal-Token", properties.getInternalToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<PatientRes>>() {});
            if (body == null || !body.isSuccess() || body.getData() == null) {
                return null;
            }
            PatientRes row = body.getData();
            return new PatientRecord(
                    row.id(), accountId, row.name(), row.gender(), row.age(), row.relation(), row.isDefault());
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_FOUND);
        }
    }

    @Override
    public PatientRecord insert(PatientRecord patient) {
        throw new UnsupportedOperationException("新建就诊人在身份服务");
    }

    @Override
    public boolean isEmpty(String accountId) {
        throw new UnsupportedOperationException("就诊人列表在身份服务");
    }
}
