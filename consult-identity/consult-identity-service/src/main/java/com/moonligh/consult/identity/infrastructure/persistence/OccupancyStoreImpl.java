package com.moonligh.consult.identity.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.moonligh.consult.identity.domain.OccupancyRow;
import com.moonligh.consult.identity.domain.OccupancyStore;
import com.moonligh.consult.identity.infrastructure.persistence.OccupancyMapper;

/**
 * 医生占坑表。同一订单幂等。
 */
@Repository
public class OccupancyStoreImpl implements OccupancyStore {

    private final OccupancyMapper occupancy;

    public OccupancyStoreImpl(OccupancyMapper occupancy) {
        this.occupancy = occupancy;
    }

    @Override
    public OccupancyRow find(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        List<OccupancyRow> rows = occupancy.find(orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insert(OccupancyRow row) {
        occupancy.insert(
                row.orderId(),
                row.doctorId(),
                row.scheduleId(),
                row.status(),
                Timestamp.from(row.occupiedAt()),
                Timestamp.from(row.expiresAt()));
    }

    @Override
    public boolean markReleased(String orderId, int reason, Instant at) {
        // status=1 保证两个人同时结束时只有一行能改成功
        return occupancy.markReleased(reason, Timestamp.from(at), orderId) == 1;
    }

    @Override
    public boolean updateExpiry(String orderId, Instant expiresAt) {
        return occupancy.updateExpiry(Timestamp.from(expiresAt), orderId) == 1;
    }

    @Override
    public List<OccupancyRow> claimExpired(Instant now, int limit) {
        // SKIP LOCKED：另一台机器正在处理的行直接跳过，不会互相堵住
        return occupancy.claimExpired(Timestamp.from(now), limit);
    }
}
