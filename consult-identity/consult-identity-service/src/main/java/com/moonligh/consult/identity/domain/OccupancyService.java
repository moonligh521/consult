package com.moonligh.consult.identity.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import com.moonligh.consult.identity.api.DoctorAdminRes;
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

    public OccupancyService(OccupancyStore store, Consumer<String> releaseFlight, Clock clock) {
        this(store, releaseFlight, clock, null, null);
    }

    public OccupancyService(
            OccupancyStore store,
            Consumer<String> releaseFlight,
            Clock clock,
            DoctorStore doctors,
            SlotFreedSink slotFreed) {
        this.store = store;
        this.releaseFlight = releaseFlight;
        this.clock = clock;
        this.doctors = doctors;
        this.slotFreed = slotFreed == null ? SlotFreedSink.NONE : slotFreed;
    }

    public OccupancyRow find(String orderId) {
        return store.find(orderId);
    }

    public void occupy(String orderId, String doctorId, String scheduleId, int minutes) {
        // 同一订单已经占过就不再写，支付重试不会把 in_flight 加两次
        if (orderId == null || orderId.isBlank() || store.find(orderId) != null) {
            return;
        }
        int length = minutes > 0 ? minutes : 15;
        Instant now = clock.instant();
        store.insert(new OccupancyRow(
                orderId,
                doctorId,
                scheduleId,
                OccupancyRow.ACTIVE,
                now,
                now.plus(Duration.ofMinutes(length)),
                null));
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
        slotFreed.freed(doctor.specialty(), DoctorGrade.ofTitle(doctor.title()));
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
