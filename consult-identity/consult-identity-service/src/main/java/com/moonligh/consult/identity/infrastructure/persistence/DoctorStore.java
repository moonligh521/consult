package com.moonligh.consult.identity.infrastructure.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorCoverageRes;
import com.moonligh.consult.identity.api.DoctorSaveReq;
import com.moonligh.consult.identity.domain.DoctorDirectory;
import com.moonligh.consult.identity.domain.DoctorMatcher;
import com.moonligh.consult.identity.infrastructure.persistence.DoctorMapper;

/**
 * 医生档案。一级科室、二级科室、擅长分列。
 */
@Repository
public class DoctorStore implements DoctorDirectory {

    private final DoctorMapper doctors;

    public DoctorStore(DoctorMapper doctors) {
        this.doctors = doctors;
    }

    public List<String[]> listEnabledSpecialties() {
        try {
            return doctors.listEnabledSpecialties().stream()
                    .map(row -> new String[] {String.valueOf(row.code()), row.name()})
                    .toList();
        } catch (RuntimeException error) {
            return List.of();
        }
    }

    public int count() {
        Integer n = doctors.count();
        return n == null ? 0 : n;
    }

    /**
     * 有医生在接诊台点过上线的二级科。只认 presence，不看心跳。号满了仍算在线。
     */
    public List<String> listOnlineSpecialties() {
        return doctors.listOnlineSpecialties();
    }

    /** 某二级科在职医生数。 */
    public DoctorCoverageRes coverage(int departmentCode, String specialty) {
        Integer doctorCount = doctors.countActive(departmentCode, specialty);
        Integer onDuty = doctors.countOnDuty(departmentCode, specialty);
        Integer remain = doctors.sumRemain(departmentCode, specialty);
        return new DoctorCoverageRes(
                doctorCount == null ? 0 : doctorCount,
                onDuty == null ? 0 : onDuty,
                remain == null ? 0 : remain);
    }

    public int countBySpecialty(String specialty) {
        Integer n = doctors.countBySpecialty(specialty);
        return n == null ? 0 : n;
    }

    public List<DoctorAdminRes> listBySpecialty(String specialty) {
        return doctors.listBySpecialty(specialty);
    }

    public boolean exists(String id) {
        Integer n = doctors.countById(id);
        return n != null && n > 0;
    }

    public List<DoctorAdminRes> list() {
        return doctors.list();
    }

    public DoctorAdminRes find(String id) {
        List<DoctorAdminRes> rows = doctors.find(id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public DoctorAdminRes insert(DoctorSaveReq req) {
        String id = UUID.randomUUID().toString();
        doctors.insert(
                id,
                req.name().trim(),
                req.gender(),
                req.title().trim(),
                req.hospital().trim(),
                req.departmentCode(),
                req.specialty().trim(),
                blankToNull(req.skills()),
                blankToNull(req.intro()),
                req.status() == null ? 1 : req.status());
        return find(id);
    }

    public void seed(
            String id,
            String name,
            int gender,
            String title,
            String hospital,
            int departmentCode,
            String specialty,
            String skills,
            String intro) {
        doctors.seed(id, name, gender, title, hospital, departmentCode, specialty, skills, intro);
    }

    public DoctorAdminRes update(String id, DoctorSaveReq req) {
        doctors.update(
                id,
                req.name().trim(),
                req.gender(),
                req.title().trim(),
                req.hospital().trim(),
                req.departmentCode(),
                req.specialty().trim(),
                blankToNull(req.skills()),
                blankToNull(req.intro()),
                req.status() == null ? 1 : req.status());
        return find(id);
    }

    public void backfillSkills() {
        doctors.backfillSkills();
    }

    /**
     * 派单候选：只取 DoctorMatcher 会看的三层——同二级科、同一级科、全科兜底（一级码 4 或二级「全科」），
     * 不再每单把全院 7 天排班拉回内存。
     */
    public List<DoctorMatcher.Candidate> listOnDuty(String specialty, Integer departmentCode) {
        String specialtyArg = specialty == null ? "" : specialty;
        int departmentArg = departmentCode == null ? -1 : departmentCode;
        try {
            return doctors.listOnDuty(specialtyArg, departmentArg);
        } catch (RuntimeException error) {
            return doctors.listOnDutyFallback(specialtyArg, departmentArg);
        }
    }

    public boolean occupy(String doctorId, String scheduleId) {
        int doctor = doctors.incrementFlight(doctorId);
        if (doctor != 1) {
            return false;
        }
        int slot = doctors.decrementRemain(scheduleId);
        if (slot != 1) {
            doctors.decrementFlight(doctorId);
            return false;
        }
        return true;
    }

    /** 占坑行没插进去时撤回这一次扣减。号源和在途一起还。 */
    public void undoOccupy(String doctorId, String scheduleId) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        doctors.decrementFlight(doctorId);
        if (scheduleId == null || scheduleId.isBlank()) {
            return;
        }
        doctors.restoreRemain(scheduleId);
    }

    /** 接诊台心跳。只给已经点过上线的医生续时间，不会把人标成在线。 */
    public void heartbeat(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        doctors.heartbeat(doctorId);
    }

    /** 医生在接诊台点击上线。 */
    public void goOnline(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        doctors.goOnline(doctorId);
    }

    /** 演示环境没有医生端时，给已经在线的人续心跳。生产关掉。 */
    public int refreshPresentBeats() {
        return doctors.refreshPresentBeats();
    }

    /** 演示心跳打开时，把在档医生标成已点击上线。生产不走这里。 */
    public int markSeededOnline() {
        return doctors.markSeededOnline();
    }

    public void goOffline(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        doctors.goOffline(doctorId);
    }

    /** 在线状态只由接诊台的上线、下线按钮改。心跳中断不在这里把人改成下线。 */
    public int offlineStale() {
        return 0;
    }

    public boolean isOnline(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return false;
        }
        List<Integer> rows = doctors.selectPresence(doctorId);
        if (rows.isEmpty()) {
            throw new EmptyResultDataAccessException(1);
        }
        if (rows.size() > 1) {
            throw new IncorrectResultSizeDataAccessException(1, rows.size());
        }
        Integer presence = rows.get(0);
        return presence != null && presence == 1;
    }

    public List<PresentDoctor> listPresent() {
        return doctors.listPresent();
    }

    /** 问诊结束或超时释放图文坑。remain_num 不退，号源已用。 */
    public void releaseFlight(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            return;
        }
        doctors.decrementFlight(doctorId);
    }

    public record PresentDoctor(
            String id, String specialty, String title, int inFlight, long idleSinceMillis, int concurrentLimit) {
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
