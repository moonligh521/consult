package com.moonligh.consult.agent.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ActiveVisitMapper {

    int insert(
            @Param("accountId") String accountId,
            @Param("patientId") String patientId,
            @Param("consultationId") String consultationId);

    int release(
            @Param("accountId") String accountId,
            @Param("patientId") String patientId,
            @Param("consultationId") String consultationId);

    List<String> find(@Param("accountId") String accountId, @Param("patientId") String patientId);
}
