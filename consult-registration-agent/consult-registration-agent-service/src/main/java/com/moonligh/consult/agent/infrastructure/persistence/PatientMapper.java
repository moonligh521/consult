package com.moonligh.consult.agent.infrastructure.persistence;

import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PatientMapper {

    List<PatientRow> list(@Param("accountId") String accountId);

    List<PatientRow> find(@Param("patientId") String patientId, @Param("accountId") String accountId);

    int insert(
            @Param("id") String id,
            @Param("accountId") String accountId,
            @Param("name") String name,
            @Param("gender") int gender,
            @Param("age") int age,
            @Param("relation") String relation,
            @Param("isDefault") int isDefault);

    Integer countByAccount(@Param("accountId") String accountId);

    record PatientRow(
        String id,
        String accountId,
        String name,
        Integer gender,
        Integer age,
        String relation,
        Integer isDefault,
        Instant updatedAt) {
    }
}
