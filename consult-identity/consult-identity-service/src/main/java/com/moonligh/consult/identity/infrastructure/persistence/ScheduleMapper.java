package com.moonligh.consult.identity.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.consult.identity.api.ScheduleAdminRes;

@Mapper
public interface ScheduleMapper {

    List<ScheduleAdminRes> listByDoctorAndRange(
            @Param("doctorId") String doctorId, @Param("from") String from, @Param("to") String to);

    List<ScheduleAdminRes> listByDoctor(@Param("doctorId") String doctorId);

    List<ScheduleAdminRes> listByRange(@Param("from") String from, @Param("to") String to);

    List<ScheduleAdminRes> listUpcoming();

    List<ScheduleAdminRes> find(@Param("id") String id);

    void insert(
            @Param("id") String id,
            @Param("doctorId") String doctorId,
            @Param("visitDate") LocalDate visitDate,
            @Param("weekday") int weekday,
            @Param("period") String period,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            @Param("visitType") String visitType,
            @Param("limitNum") int limitNum,
            @Param("remainNum") int remainNum);

    List<ScheduleAdminRes> findSlot(
            @Param("doctorId") String doctorId, @Param("visitDate") LocalDate visitDate, @Param("period") String period);

    void stop(@Param("stopped") int stopped, @Param("id") String id);

    void delete(@Param("id") String id);
}
