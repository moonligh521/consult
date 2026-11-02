package com.moonligh.consult.identity.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorSaveReq;
import com.moonligh.consult.identity.api.ScheduleAdminRes;
import com.moonligh.consult.identity.api.ScheduleSaveReq;
import com.moonligh.consult.identity.store.DoctorStore;
import com.moonligh.consult.identity.store.ScheduleStore;

/**
 * HIS：医生档案与出诊排班。
 */
@Service
public class DoctorAdminService {

    private static final Set<String> PERIODS = Set.of("上午", "下午", "晚上");
    private static final Set<String> TITLES = Set.of("住院医师", "主治医师", "副主任医师", "主任医师");

    private final DoctorStore doctors;
    private final ScheduleStore schedules;

    public DoctorAdminService(DoctorStore doctors, ScheduleStore schedules) {
        this.doctors = doctors;
        this.schedules = schedules;
    }

    public List<DoctorAdminRes> listDoctors() {
        return doctors.list();
    }

    public DoctorAdminRes createDoctor(DoctorSaveReq req) {
        return doctors.insert(requireDoctor(req));
    }

    public DoctorAdminRes updateDoctor(String id, DoctorSaveReq req) {
        requireDoctorId(id);
        return doctors.update(id, requireDoctor(req));
    }

    public List<ScheduleAdminRes> listSchedules(String doctorId, String from, String to) {
        return schedules.list(doctorId, from, to);
    }

    public ScheduleAdminRes createSchedule(ScheduleSaveReq req) {
        if (req == null || req.doctorId() == null || req.doctorId().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        requireDoctorId(req.doctorId());
        LocalDate date = parseDate(req.visitDate());
        String period = req.period() == null ? "" : req.period().trim();
        if (!PERIODS.contains(period)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        String start = timeOf(req.startTime(), period, true);
        String end = timeOf(req.endTime(), period, false);
        return schedules.insert(
                new ScheduleSaveReq(req.doctorId(), date.toString(), period, start, end, req.visitType(), req.limitNum()),
                date,
                weekday(date));
    }

    public void stopSchedule(String id, boolean stopped) {
        if (schedules.find(id) == null) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        schedules.stop(id, stopped);
    }

    public void deleteSchedule(String id) {
        if (schedules.find(id) == null) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        schedules.delete(id);
    }

    private DoctorAdminRes requireDoctorId(String id) {
        DoctorAdminRes row = doctors.find(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        return row;
    }

    private DoctorSaveReq requireDoctor(DoctorSaveReq req) {
        if (req == null || req.departmentCode() == null) {
            throw new BusinessException(ErrorCode.INVALID_DOCTOR);
        }
        String name = req.name() == null ? "" : req.name().trim();
        String title = req.title() == null ? "" : req.title().trim();
        String hospital = req.hospital() == null ? "" : req.hospital().trim();
        String specialty = req.specialty() == null ? "" : req.specialty().trim();
        String skills = req.skills() == null ? "" : req.skills().trim();
        if (name.isEmpty() || title.isEmpty() || hospital.isEmpty() || specialty.isEmpty() || skills.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_DOCTOR);
        }
        if (!TITLES.contains(title)) {
            throw new BusinessException(ErrorCode.INVALID_DOCTOR);
        }
        int gender = req.gender() == null ? 1 : req.gender();
        if (gender != 1 && gender != 2) {
            throw new BusinessException(ErrorCode.INVALID_GENDER);
        }
        return new DoctorSaveReq(
                name,
                gender,
                title,
                hospital,
                req.departmentCode(),
                specialty,
                skills,
                req.intro(),
                req.status() == null ? 1 : req.status());
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException error) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
    }

    private int weekday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day.getValue();
    }

    private String timeOf(String raw, String period, boolean start) {
        if (raw != null && raw.matches("\\d{2}:\\d{2}")) {
            return raw;
        }
        return switch (period) {
            case "上午" -> start ? "08:00" : "12:00";
            case "下午" -> start ? "14:00" : "17:30";
            default -> start ? "18:00" : "21:00";
        };
    }
}
