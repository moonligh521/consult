package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.consult.identity.infrastructure.persistence.StaffStore.StaffRow;

@Mapper
public interface StaffMapper {

    Integer count();

    void insert(
            @Param("id") String id,
            @Param("username") String username,
            @Param("passwordHash") String passwordHash,
            @Param("name") String name);

    List<StaffRow> findByUsername(@Param("username") String username);

    List<StaffRow> findById(@Param("id") String id);

    void bindDoctor(@Param("doctorId") String doctorId, @Param("username") String username);
}
