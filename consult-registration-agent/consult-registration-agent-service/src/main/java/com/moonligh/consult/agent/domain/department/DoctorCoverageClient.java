package com.moonligh.consult.agent.domain.department;

/**
 * 向身份服务问覆盖率、演示补人。
 */
public interface DoctorCoverageClient {

    DoctorCoverage coverage(int departmentCode, String specialty);

    DoctorCoverage ensureDemo(int departmentCode, String specialty);
}
