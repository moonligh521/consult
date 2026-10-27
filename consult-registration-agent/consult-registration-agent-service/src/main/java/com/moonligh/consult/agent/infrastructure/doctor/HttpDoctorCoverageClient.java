package com.moonligh.consult.agent.infrastructure.doctor;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Repository;
import org.springframework.web.client.RestClient;

import com.moonligh.consult.agent.config.IdentityProperties;
import com.moonligh.consult.agent.domain.department.DoctorCoverage;
import com.moonligh.consult.agent.domain.department.DoctorCoverageClient;
import com.moonligh.consult.framework.Result;

/**
 * 身份服务上的覆盖率与演示补人。
 */
@Repository
public class HttpDoctorCoverageClient implements DoctorCoverageClient {

    private final RestClient client;
    private final IdentityProperties properties;

    public HttpDoctorCoverageClient(RestClient identityRestClient, IdentityProperties properties) {
        this.client = identityRestClient;
        this.properties = properties;
    }

    @Override
    public DoctorCoverage coverage(int departmentCode, String specialty) {
        try {
            Result<DoctorCoverage> body = client.get()
                    .uri("/internal/doctors/coverage?departmentCode={code}&specialty={spec}",
                            departmentCode, specialty)
                    .header("X-Internal-Token", properties.getInternalToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<DoctorCoverage>>() {});
            if (body == null || body.getData() == null) {
                return new DoctorCoverage(0, 0, 0);
            }
            return body.getData();
        } catch (RuntimeException error) {
            return new DoctorCoverage(0, 0, 0);
        }
    }

    @Override
    public DoctorCoverage ensureDemo(int departmentCode, String specialty) {
        try {
            Result<DoctorCoverage> body = client.post()
                    .uri("/internal/doctors/ensure-demo-coverage?departmentCode={code}&specialty={spec}",
                            departmentCode, specialty)
                    .header("X-Internal-Token", properties.getInternalToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Result<DoctorCoverage>>() {});
            if (body == null || body.getData() == null) {
                return coverage(departmentCode, specialty);
            }
            return body.getData();
        } catch (RuntimeException error) {
            return coverage(departmentCode, specialty);
        }
    }
}
