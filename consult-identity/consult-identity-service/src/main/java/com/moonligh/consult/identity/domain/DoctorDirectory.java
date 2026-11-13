package com.moonligh.consult.identity.domain;

import com.moonligh.consult.identity.api.DoctorAdminRes;

/** 占坑释放时读取医生科室和职称。存储实现在持久化包。 */
public interface DoctorDirectory {

    DoctorAdminRes find(String id);
}
