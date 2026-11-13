package com.moonligh.consult.identity.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StaffSchemaMapper {

    Integer countDoctorIdColumn();

    void addDoctorIdColumn();
}
