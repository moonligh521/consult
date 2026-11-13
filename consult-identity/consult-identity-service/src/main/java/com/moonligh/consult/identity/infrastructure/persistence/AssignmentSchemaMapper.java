package com.moonligh.consult.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssignmentSchemaMapper {

    Integer countColumn(@Param("table") String table, @Param("name") String name);

    void addDoctorIdleSince();

    void addDoctorLastBeat();

    void addDoctorPresence();

    void createOutbox();

    void addOutboxClaimToken();

    void addOutboxClaimedAt();

    void createInbox();
}
