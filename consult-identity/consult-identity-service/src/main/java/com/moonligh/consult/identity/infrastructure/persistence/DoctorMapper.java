package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.domain.DoctorMatcher;
import com.moonligh.consult.identity.infrastructure.persistence.DoctorStore.PresentDoctor;

@Mapper
public interface DoctorMapper {

    record SpecialtyCode(int code, String name) {
    }

    List<SpecialtyCode> listEnabledSpecialties();

    Integer count();

    List<String> listOnlineSpecialties();

    Integer countActive(@Param("departmentCode") int departmentCode, @Param("specialty") String specialty);

    Integer countOnDuty(@Param("departmentCode") int departmentCode, @Param("specialty") String specialty);

    Integer sumRemain(@Param("departmentCode") int departmentCode, @Param("specialty") String specialty);

    Integer countBySpecialty(@Param("specialty") String specialty);

    List<DoctorAdminRes> listBySpecialty(@Param("specialty") String specialty);

    Integer countById(@Param("id") String id);

    List<DoctorAdminRes> list();

    List<DoctorAdminRes> find(@Param("id") String id);

    void insert(
            @Param("id") String id,
            @Param("name") String name,
            @Param("gender") Integer gender,
            @Param("title") String title,
            @Param("hospital") String hospital,
            @Param("departmentCode") Integer departmentCode,
            @Param("specialty") String specialty,
            @Param("skills") String skills,
            @Param("intro") String intro,
            @Param("status") int status);

    void seed(
            @Param("id") String id,
            @Param("name") String name,
            @Param("gender") int gender,
            @Param("title") String title,
            @Param("hospital") String hospital,
            @Param("departmentCode") int departmentCode,
            @Param("specialty") String specialty,
            @Param("skills") String skills,
            @Param("intro") String intro);

    void update(
            @Param("id") String id,
            @Param("name") String name,
            @Param("gender") Integer gender,
            @Param("title") String title,
            @Param("hospital") String hospital,
            @Param("departmentCode") Integer departmentCode,
            @Param("specialty") String specialty,
            @Param("skills") String skills,
            @Param("intro") String intro,
            @Param("status") int status);

    void backfillSkills();

    List<DoctorMatcher.Candidate> listOnDuty(
            @Param("specialty") String specialty, @Param("departmentCode") int departmentCode);

    List<DoctorMatcher.Candidate> listOnDutyFallback(
            @Param("specialty") String specialty, @Param("departmentCode") int departmentCode);

    int incrementFlight(@Param("doctorId") String doctorId);

    int decrementRemain(@Param("scheduleId") String scheduleId);

    int decrementFlight(@Param("doctorId") String doctorId);

    int restoreRemain(@Param("scheduleId") String scheduleId);

    void heartbeat(@Param("doctorId") String doctorId);

    void goOnline(@Param("doctorId") String doctorId);

    int refreshPresentBeats();

    int markSeededOnline();

    void goOffline(@Param("doctorId") String doctorId);

    List<Integer> selectPresence(@Param("doctorId") String doctorId);

    List<PresentDoctor> listPresent();
}
