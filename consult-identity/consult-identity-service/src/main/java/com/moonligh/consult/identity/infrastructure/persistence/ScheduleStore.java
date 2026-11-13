package com.moonligh.consult.identity.infrastructure.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.ScheduleAdminRes;
import com.moonligh.consult.identity.api.ScheduleSaveReq;
import com.moonligh.consult.identity.infrastructure.persistence.ScheduleMapper;

/**
 * 出诊排班。
 */
@Repository
public class ScheduleStore {

    private final ScheduleMapper schedules;

    public ScheduleStore(ScheduleMapper schedules) {
        this.schedules = schedules;
    }

    public List<ScheduleAdminRes> list(String doctorId, String from, String to) {
        if (doctorId != null && !doctorId.isBlank() && from != null && !from.isBlank() && to != null && !to.isBlank()) {
            return schedules.listByDoctorAndRange(doctorId.trim(), from, to);
        }
        if (doctorId != null && !doctorId.isBlank()) {
            return schedules.listByDoctor(doctorId.trim());
        }
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            return schedules.listByRange(from, to);
        }
        return schedules.listUpcoming();
    }

    public ScheduleAdminRes find(String id) {
        List<ScheduleAdminRes> rows = schedules.find(id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public ScheduleAdminRes insert(ScheduleSaveReq req, LocalDate date, int weekday) {
        String id = UUID.randomUUID().toString();
        int limit = req.limitNum() == null || req.limitNum() < 1 ? 20 : req.limitNum();
        String visitType = req.visitType() == null || req.visitType().isBlank() ? "图文" : req.visitType().trim();
        try {
            schedules.insert(
                    id,
                    req.doctorId(),
                    date,
                    weekday,
                    req.period(),
                    req.startTime(),
                    req.endTime(),
                    visitType,
                    limit,
                    limit);
        } catch (DuplicateKeyException error) {
            ScheduleAdminRes existing = findSlot(req.doctorId(), date, req.period());
            if (existing != null) {
                return existing;
            }
            throw new BusinessException(ErrorCode.SCHEDULE_CONFLICT);
        }
        return find(id);
    }

    private ScheduleAdminRes findSlot(String doctorId, LocalDate date, String period) {
        List<ScheduleAdminRes> rows = schedules.findSlot(doctorId, date, period);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void stop(String id, boolean stopped) {
        schedules.stop(stopped ? 1 : 0, id);
    }

    public void delete(String id) {
        schedules.delete(id);
    }
}
