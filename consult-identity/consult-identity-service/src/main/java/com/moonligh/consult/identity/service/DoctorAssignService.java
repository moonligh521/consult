package com.moonligh.consult.identity.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.moonligh.consult.identity.domain.AssignmentNoticeStore;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.identity.api.AssignDoctorReq;
import com.moonligh.consult.identity.api.AssignDoctorRes;
import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.DoctorCoverageRes;
import com.moonligh.consult.identity.domain.DoctorGrade;
import com.moonligh.consult.identity.domain.DoctorMatcher;
import com.moonligh.consult.identity.domain.OccupancyRow;
import com.moonligh.consult.identity.domain.OccupancyService;
import com.moonligh.consult.identity.domain.load.DoctorClaim;
import com.moonligh.consult.identity.domain.load.DoctorLoadPool;
import com.moonligh.consult.identity.domain.load.DoctorSeat;
import com.moonligh.consult.identity.infrastructure.persistence.DoctorStore;

/**
 * 支付后派医生。按擅长选人，选中再占坑。同一订单只派一次。
 */
@Service
public class DoctorAssignService {

    private static final Logger log = LoggerFactory.getLogger(DoctorAssignService.class);

    private final DoctorStore doctors;
    private final OccupancyService occupancy;
    private DoctorLoadPool loadPool;
    private AssignmentNoticeStore notices;
    private AssignmentPublisher publisher;

    public DoctorAssignService(DoctorStore doctors, OccupancyService occupancy) {
        this.doctors = doctors;
        this.occupancy = occupancy;
    }

    @Autowired(required = false)
    public void setLoadPool(DoctorLoadPool loadPool) {
        this.loadPool = loadPool;
    }

    @Autowired(required = false)
    public void setNotices(AssignmentNoticeStore notices) {
        this.notices = notices;
    }

    @Autowired(required = false)
    public void setPublisher(AssignmentPublisher publisher) {
        this.publisher = publisher;
    }

    /** 读已提交：并发输家要看见对方已经提交的占坑行，再把这次多扣的号源撤掉。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AssignDoctorRes assign(AssignDoctorReq req) {
        if (req == null) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        AssignDoctorRes existing = alreadyAssigned(req.orderId());
        if (existing != null) {
            return existing;
        }
        String text = haystack(req);
        List<DoctorMatcher.Candidate> onDuty = doctors.listOnDuty(req.specialty(), req.departmentCode());
        List<Integer> grades = DoctorGrade.searchOrder(req.packageCode(), Boolean.TRUE.equals(req.allowUpgrade()));
        List<String> failedScheduleIds = new ArrayList<>();
        if (grades == null) {
            AssignDoctorRes hit = occupyFirst(onDuty, failedScheduleIds, req, text, null);
            if (hit != null) {
                return hit;
            }
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
        for (int grade : grades) {
            AssignDoctorRes hit = occupyFirst(
                    DoctorGrade.ofGrade(onDuty, grade), failedScheduleIds, req, text, grade);
            if (hit != null) {
                return hit;
            }
        }
        throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
    }

    /** 当前时段该科有出诊号才收款。档位空缺由派单提档处理。 */
    public boolean available(String specialty, Integer departmentCode, Integer packageCode) {
        List<DoctorMatcher.Candidate> onDuty = doctors.listOnDuty(specialty, departmentCode);
        if (onDuty == null || onDuty.isEmpty()) {
            return false;
        }
        if (packageCode == null || packageCode < 1 || packageCode > 4) {
            return true;
        }
        List<Integer> grades = DoctorGrade.searchOrder(packageCode, true);
        if (grades == null) {
            return true;
        }
        for (int grade : grades) {
            if (!DoctorGrade.ofGrade(onDuty, grade).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 接诊台心跳。只续已经点过上线的医生，不会把下线的人改成在线。 */
    public void heartbeat(String doctorId) {
        doctors.heartbeat(doctorId);
    }

    /** 医生在接诊台点击上线。心跳不会代替这次点击。 */
    public void online(String doctorId) {
        doctors.goOnline(doctorId);
        DoctorAdminRes doctor = requireDoctor(doctorId);
        if (loadPool == null) {
            return;
        }
        loadPool.online(
                doctor.id(),
                doctor.specialty(),
                DoctorGrade.ofTitle(doctor.title()),
                doctor.inFlight(),
                doctor.concurrentLimit(),
                System.currentTimeMillis());
    }

    /** 医生下线。已在手的单不退，只是不再领新单。 */
    public void offline(String doctorId) {
        DoctorAdminRes doctor = requireDoctor(doctorId);
        doctors.goOffline(doctor.id());
        if (loadPool == null) {
            return;
        }
        loadPool.offline(doctor.id(), doctor.specialty(), DoctorGrade.ofTitle(doctor.title()));
    }

    /** 当前档里按擅长圈人，再按在手单数队列领取。队列不可用时退回原来的选人。 */
    private AssignDoctorRes occupyFirst(
            List<DoctorMatcher.Candidate> pool,
            List<String> failedScheduleIds,
            AssignDoctorReq req,
            String text,
            Integer grade) {
        if (loadPool != null && grade != null && req.orderId() != null && !req.orderId().isBlank()) {
            try {
                return occupyFair(pool, failedScheduleIds, req, text, grade);
            } catch (RuntimeException error) {
                log.warn("公平队列不可用，改走数据库选人 orderId={}", req.orderId());
            }
        }
        return occupyLegacy(pool, failedScheduleIds, req, text);
    }

    private AssignDoctorRes occupyFair(
            List<DoctorMatcher.Candidate> pool,
            List<String> failedScheduleIds,
            AssignDoctorReq req,
            String text,
            int grade) {
        List<String> failedDoctors = new ArrayList<>();
        while (true) {
            List<DoctorMatcher.Candidate> remain = leftover(pool, failedScheduleIds);
            DoctorMatcher.Dispatch dispatch = DoctorMatcher.dispatch(
                    remain, req.specialty(), req.departmentCode(), text, req.canonicalTerms());
            List<DoctorSeat> seats = seatsOf(dispatch.doctors(), failedDoctors);
            if (seats.isEmpty()) {
                return null;
            }
            LaneClaim lane = claimOnOwnLane(
                    req.orderId(), grade, dispatch.doctors(), failedDoctors, System.currentTimeMillis());
            if (lane == null) {
                return occupyLegacy(pool, failedScheduleIds, req, text);
            }
            DoctorClaim claim = lane.claim();
            String specialty = lane.specialty();
            armRollback(req.orderId(), claim, specialty, grade);
            DoctorMatcher.Candidate doctor = find(remain, claim.doctorId());
            if (doctor == null) {
                disarmRollback();
                loadPool.restore(req.orderId(), claim, specialty, grade);
                return null;
            }
            if (!doctors.occupy(doctor.id(), doctor.scheduleId())) {
                disarmRollback();
                loadPool.restore(req.orderId(), claim, specialty, grade);
                failedScheduleIds.add(doctor.scheduleId());
                failedDoctors.add(doctor.id());
                continue;
            }
            if (!recordOccupancy(req, doctor)) {
                doctors.undoOccupy(doctor.id(), doctor.scheduleId());
                AssignDoctorRes prior = alreadyAssigned(req.orderId());
                disarmRollback();
                if (prior != null) {
                    return prior;
                }
                loadPool.restore(req.orderId(), claim, specialty, grade);
                failedScheduleIds.add(doctor.scheduleId());
                failedDoctors.add(doctor.id());
                continue;
            }
            return assigned(doctor, text, req, dispatch.fallback());
        }
    }

    /**
     * 计数和车道都落在医生自己的二级科上，和释放、上线、按库重建用同一把键。
     * 请求科和兜底科不是同一个时，不能把增量记在请求科上。
     */
    private LaneClaim claimOnOwnLane(
            String orderId,
            int grade,
            List<DoctorMatcher.Candidate> doctors,
            List<String> failedDoctors,
            long nowMillis) {
        LinkedHashMap<String, List<DoctorSeat>> groups = new LinkedHashMap<>();
        LinkedHashMap<String, String> specialtyOf = new LinkedHashMap<>();
        for (DoctorMatcher.Candidate row : doctors) {
            if (row == null || failedDoctors.contains(row.id()) || row.inFlight() >= row.concurrentLimit()) {
                continue;
            }
            String specialty = row.specialty() == null || row.specialty().isBlank() ? "_" : row.specialty();
            groups.computeIfAbsent(specialty, key -> new ArrayList<>())
                    .add(new DoctorSeat(row.id(), row.inFlight(), row.idleSinceMillis(), row.concurrentLimit()));
            specialtyOf.put(row.id(), specialty);
        }
        for (Map.Entry<String, List<DoctorSeat>> group : groups.entrySet()) {
            DoctorClaim claim = loadPool.claim(orderId, group.getKey(), grade, group.getValue(), nowMillis);
            if (claim == null) {
                continue;
            }
            String specialty = specialtyOf.get(claim.doctorId());
            if (specialty == null) {
                specialty = specialtyOf(doctors, claim.doctorId());
            }
            if (specialty == null) {
                specialty = group.getKey();
            }
            return new LaneClaim(claim, specialty);
        }
        return null;
    }

    private static List<DoctorSeat> seatsOf(List<DoctorMatcher.Candidate> doctors, List<String> failedDoctors) {
        List<DoctorSeat> seats = new ArrayList<>();
        for (DoctorMatcher.Candidate row : doctors) {
            if (row == null || failedDoctors.contains(row.id()) || row.inFlight() >= row.concurrentLimit()) {
                continue;
            }
            seats.add(new DoctorSeat(row.id(), row.inFlight(), row.idleSinceMillis(), row.concurrentLimit()));
        }
        return seats;
    }

    private static String specialtyOf(List<DoctorMatcher.Candidate> doctors, String doctorId) {
        for (DoctorMatcher.Candidate row : doctors) {
            if (row != null && doctorId.equals(row.id()) && row.specialty() != null && !row.specialty().isBlank()) {
                return row.specialty();
            }
        }
        return null;
    }

    private record LaneClaim(DoctorClaim claim, String specialty) {
    }

    private void armRollback(String orderId, DoctorClaim claim, String specialty, int grade) {
        if (!TransactionSynchronizationManager.isSynchronizationActive() || loadPool == null) {
            return;
        }
        AtomicReference<LiveClaim> box = liveClaim();
        if (box == null) {
            box = new AtomicReference<>();
            TransactionSynchronizationManager.bindResource(LIVE_CLAIM, box);
            AtomicReference<LiveClaim> bound = box;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(LIVE_CLAIM)) {
                        TransactionSynchronizationManager.unbindResource(LIVE_CLAIM);
                    }
                    LiveClaim live = bound.get();
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK && live != null) {
                        loadPool.restore(live.orderId(), live.claim(), live.specialty(), live.grade());
                    }
                }
            });
        }
        box.set(new LiveClaim(orderId, claim, specialty, grade));
    }

    private void disarmRollback() {
        AtomicReference<LiveClaim> box = liveClaim();
        if (box != null) {
            box.set(null);
        }
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<LiveClaim> liveClaim() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.hasResource(LIVE_CLAIM)) {
            return null;
        }
        return (AtomicReference<LiveClaim>) TransactionSynchronizationManager.getResource(LIVE_CLAIM);
    }

    private record LiveClaim(String orderId, DoctorClaim claim, String specialty, int grade) {
    }

    private static final Object LIVE_CLAIM = new Object();

    /** 当前档里按擅长选人并占坑。这一档没人就返回 null，让调用方试下一档。 */
    private AssignDoctorRes occupyLegacy(
            List<DoctorMatcher.Candidate> pool,
            List<String> failedScheduleIds,
            AssignDoctorReq req,
            String text) {
        while (true) {
            List<DoctorMatcher.Candidate> remain = leftover(pool, failedScheduleIds);
            DoctorMatcher.Pick pick = DoctorMatcher.pick(
                    remain, req.specialty(), req.departmentCode(), text, req.canonicalTerms());
            if (pick == null) {
                return null;
            }
            DoctorMatcher.Candidate doctor = pick.doctor();
            if (!doctors.occupy(doctor.id(), doctor.scheduleId())) {
                failedScheduleIds.add(doctor.scheduleId());
                continue;
            }
            if (!recordOccupancy(req, doctor)) {
                doctors.undoOccupy(doctor.id(), doctor.scheduleId());
                AssignDoctorRes prior = alreadyAssigned(req.orderId());
                if (prior != null) {
                    return prior;
                }
                failedScheduleIds.add(doctor.scheduleId());
                continue;
            }
            return assigned(doctor, text, req, pick.source());
        }
    }

    /** @return false 这张订单已经被别的请求占走，调用方撤回刚才的号源 */
    private boolean recordOccupancy(AssignDoctorReq req, DoctorMatcher.Candidate doctor) {
        if (req.orderId() == null || req.orderId().isBlank()) {
            return true;
        }
        int minutes = req.consultMinutes() == null || req.consultMinutes() <= 0 ? 15 : req.consultMinutes();
        if (!occupancy.occupy(req.orderId(), doctor.id(), doctor.scheduleId(), minutes)) {
            return false;
        }
        if (notices != null) {
            notices.record(req.orderId(), req.consultationId(), doctor.id(), req.specialty(), doctor.scheduleId());
        }
        if (publisher != null) {
            publisher.publishAfterCommit(req.orderId());
        }
        return true;
    }

    private AssignDoctorRes assigned(
            DoctorMatcher.Candidate doctor,
            String text,
            AssignDoctorReq req,
            DoctorMatcher.MatchSource fallback) {
        DoctorMatcher.Pick described = DoctorMatcher.describe(doctor, text, req.canonicalTerms());
        DoctorMatcher.MatchSource source = described.source() != null ? described.source() : fallback;
        return new AssignDoctorRes(
                doctor.id(),
                doctor.name(),
                doctor.title(),
                doctor.hospital(),
                doctor.departmentCode(),
                doctor.specialty(),
                doctor.skills(),
                described.evidence(),
                doctor.scheduleId(),
                source == null ? null : source.name());
    }

    private static DoctorMatcher.Candidate find(List<DoctorMatcher.Candidate> rows, String doctorId) {
        for (DoctorMatcher.Candidate row : rows) {
            if (doctorId.equals(row.id())) {
                return row;
            }
        }
        return null;
    }

    private DoctorAdminRes requireDoctor(String doctorId) {
        if (doctorId == null || doctorId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        DoctorAdminRes doctor = doctors.find(doctorId);
        if (doctor == null) {
            throw new BusinessException(ErrorCode.DOCTOR_NOT_FOUND);
        }
        return doctor;
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

    public boolean isOnline(String doctorId) {
        return doctors.isOnline(doctorId);
    }

    public DoctorCoverageRes coverage(int departmentCode, String specialty) {
        return doctors.coverage(departmentCode, specialty);
    }

    public List<String> listOnlineSpecialties() {
        return doctors.listOnlineSpecialties();
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
