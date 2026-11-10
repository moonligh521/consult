package com.moonligh.consult.identity.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import org.springframework.dao.DuplicateKeyException;

import com.moonligh.consult.identity.api.DoctorAdminRes;
import com.moonligh.consult.identity.api.SlotFreed;
import com.moonligh.consult.identity.domain.load.DoctorLoadPool;
import com.moonligh.consult.identity.store.DoctorStore;

/**
 * 占坑：下单写入、改时长、结束或超时释放。释放只减一次医生在途数。
 */
public class OccupancyService {

    /** 一拍最多释放多少条。满了等 XXL 下一拍。 */
    public static final int BATCH = 200;

    private final OccupancyStore store;
    private final Consumer<String> releaseFlight;
    private final Clock clock;
    private final DoctorStore doctors;
    private final SlotFreedSink slotFreed;
    private final DoctorLoadPool loadPool;

    public OccupancyService(OccupancyStore store, Consumer<String> releaseFlight, Clock clock) {
        this(store, releaseFlight, clock, null, null, null);
    }

    public OccupancyService(
            OccupancyStore store,
            Consumer<String> releaseFlight,
            Clock clock,
            DoctorStore doctors,
            SlotFreedSink slotFreed) {
        this(store, releaseFlight, clock, doctors, slotFreed, null);
    }

    public OccupancyService(
            OccupancyStore store,
            Consumer<String> releaseFlight,
            Clock clock,
            DoctorStore doctors,
            SlotFreedSink slotFreed,
            DoctorLoadPool loadPool) {
        this.store = store;
        this.releaseFlight = releaseFlight;
        this.clock = clock;
        this.doctors = doctors;
        this.slotFreed = slotFreed == null ? SlotFreedSink.NONE : slotFreed;
        this.loadPool = loadPool;
    }

    public OccupancyRow find(String orderId) {
        return store.find(orderId);
    }

    /** @return true 本事务新插入；false 这张订单已经占过，调用方必须撤回刚才的号源扣减 */
    public boolean occupy(String orderId, String doctorId, String scheduleId, int minutes) {
        if (orderId == null || orderId.isBlank() || store.find(orderId) != null) {
            return false;
        }
        int length = minutes > 0 ? minutes : 15;
        Instant now = clock.instant();
        try {
            store.insert(new OccupancyRow(
                    orderId,
                    doctorId,
                    scheduleId,
                    OccupancyRow.ACTIVE,
                    now,
                    now.plus(Duration.ofMinutes(length)),
                    null));
            return true;
        } catch (DuplicateKeyException existing) {
            return false;
        }
    }

    public boolean release(String orderId, int reason) {
        OccupancyRow row = store.find(orderId);
        if (row == null || row.status() != OccupancyRow.ACTIVE) {
            // 已经结束过，in_flight 不能再减
            return false;
        }
        if (!store.markReleased(orderId, reason, clock.instant())) {
            return false;
        }
        releaseFlight.accept(row.doctorId());
        notifyFreed(row.doctorId());
        return true;
    }

    private void notifyFreed(String doctorId) {
        if (doctors == null || doctorId == null || doctorId.isBlank()) {
            return;
        }
        DoctorAdminRes doctor = doctors.find(doctorId);
        if (doctor == null) {
            return;
        }
        if (loadPool != null) {
            try {
                loadPool.release(
                        doctor.id(),
                        doctor.specialty(),
                        DoctorGrade.ofTitle(doctor.title()),
                        clock.millis());
            } catch (RuntimeException ignored) {
                // 号已经还了。队列没减成功时，下次上线会按数据库在手单数重放。
            }
        }
        slotFreed.freed(new SlotFreed(doctor.specialty(), DoctorGrade.ofTitle(doctor.title())));
    }

    public boolean extend(String orderId, int minutes) {
        if (minutes <= 0) {
            return false;
        }
        return store.updateExpiry(orderId, clock.instant().plus(Duration.ofMinutes(minutes)));
    }

    public int reapBatch() {
        return reapBatch(BATCH);
    }

    public int reapBatch(int limit) {
        if (limit <= 0) {
            return 0;
        }
        if (limit > BATCH) {
            limit = BATCH;
        }
        int n = 0;
        for (OccupancyRow row : store.claimExpired(clock.instant(), limit)) {
            if (release(row.orderId(), OccupancyRow.TIMEOUT)) {
                n++;
            }
        }
        return n;
    }
}
