package com.moonligh.consult.identity.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.AssignDoctorReq;
import com.moonligh.consult.identity.api.AssignDoctorRes;
import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.domain.DoctorMatcher;
import com.moonligh.consult.identity.domain.OccupancyRow;
import com.moonligh.consult.identity.domain.OccupancyService;
import com.moonligh.consult.identity.store.DoctorStore;

/**
 * 支付后派医生。按擅长选人，选中再占坑。同一订单只派一次。
 */
@Service
public class DoctorAssignService {

    private final DoctorStore doctors;
    private final OccupancyService occupancy;

    public DoctorAssignService(DoctorStore doctors, OccupancyService occupancy) {
        this.doctors = doctors;
        this.occupancy = occupancy;
    }

    @Transactional
    public AssignDoctorRes assign(AssignDoctorReq req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        AssignDoctorRes existing = alreadyAssigned(req.orderId());
        if (existing != null) {
            return existing;
        }
        String text = haystack(req);
        List<DoctorMatcher.Candidate> onDuty = doctors.listOnDuty();
        List<String> failedScheduleIds = new ArrayList<>();
        while (true) {
            List<DoctorMatcher.Candidate> remain = leftover(onDuty, failedScheduleIds);
            DoctorMatcher.Pick pick = DoctorMatcher.pick(
                    remain, req.specialty(), req.departmentCode(), text, req.canonicalTerms());
            if (pick == null) {
                throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
            }
            DoctorMatcher.Candidate doctor = pick.doctor();
            if (!doctors.occupy(doctor.id(), doctor.scheduleId())) {
                // 并发把这个号占满了，换下一个
                failedScheduleIds.add(doctor.scheduleId());
                continue;
            }
            int minutes = req.consultMinutes() == null || req.consultMinutes() <= 0 ? 15 : req.consultMinutes();
            if (req.orderId() != null && !req.orderId().isBlank()) {
                occupancy.occupy(req.orderId(), doctor.id(), doctor.scheduleId(), minutes);
            }
            return new AssignDoctorRes(
                    doctor.id(),
                    doctor.name(),
                    doctor.title(),
                    doctor.hospital(),
                    doctor.departmentCode(),
                    doctor.specialty(),
                    doctor.skills(),
                    pick.evidence(),
                    doctor.scheduleId(),
                    pick.source() == null ? null : pick.source().name());
        }
    }

    @Transactional
    public boolean release(String orderId, String reason) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        return occupancy.release(orderId, OccupancyRow.reasonCode(reason));
    }

    @Transactional
    public boolean extend(String orderId, Integer minutes) {
        if (orderId == null || orderId.isBlank() || minutes == null) {
            return false;
        }
        return occupancy.extend(orderId, minutes);
    }

    /** 支付重试时订单已有占坑，直接把当时那个医生返回去。 */
    private AssignDoctorRes alreadyAssigned(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        OccupancyRow row = occupancy.find(orderId);
        if (row == null) {
            return null;
        }
        DoctorAdminRes doctor = doctors.find(row.doctorId());
        if (doctor == null) {
            return null;
        }
        return new AssignDoctorRes(
                doctor.id(),
                doctor.name(),
                doctor.title(),
                doctor.hospital(),
                doctor.departmentCode(),
                doctor.specialty(),
                doctor.skills(),
                null,
                row.scheduleId(),
                null);
    }

    private static List<DoctorMatcher.Candidate> leftover(
            List<DoctorMatcher.Candidate> onDuty,
            List<String> failedScheduleIds) {
        List<DoctorMatcher.Candidate> remain = new ArrayList<>();
        for (DoctorMatcher.Candidate row : onDuty) {
            if (!failedScheduleIds.contains(row.scheduleId())) {
                remain.add(row);
            }
        }
        return remain;
    }

    private static String haystack(AssignDoctorReq req) {
        StringBuilder text = new StringBuilder();
        if (req.complaint() != null) {
            text.append(req.complaint());
        }
        if (req.symptoms() != null) {
            for (String item : req.symptoms()) {
                if (item != null && !item.isBlank()) {
                    text.append(' ').append(item);
                }
            }
        }
        return text.toString();
    }
}
