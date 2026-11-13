package com.moonligh.consult.identity.domain.load;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 进程内三层队列。单测和 Redis 不可用时的规则对照。医生只属于一个二级科加档位。
 */
public final class MemoryDoctorLoadPool implements DoctorLoadPool {

    private final Map<String, Seat> seats = new HashMap<>();
    private final Map<String, DoctorClaim> claims = new HashMap<>();

    @Override
    public synchronized DoctorClaim claim(
            String orderId, String specialty, int grade, List<DoctorSeat> allowed, long nowMillis) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        DoctorClaim held = claims.get(orderId);
        if (held != null) {
            return held;
        }
        String laneSpecialty = specialty == null ? "" : specialty;
        ensure(laneSpecialty, grade, allowed, nowMillis);
        DoctorClaim picked = pick(laneSpecialty, grade, allowed);
        if (picked == null) {
            return null;
        }
        Seat seat = seats.get(picked.doctorId());
        seat.load = seat.load + 1;
        seat.lane = seat.load >= seat.limit ? -1 : Math.min(seat.load, 2);
        seat.score = nowMillis;
        claims.put(orderId, picked);
        return picked;
    }

    @Override
    public synchronized void restore(String orderId, DoctorClaim claim, String specialty, int grade) {
        if (orderId == null || claim == null) {
            return;
        }
        DoctorClaim held = claims.get(orderId);
        if (held == null || !claim.doctorId().equals(held.doctorId())) {
            return;
        }
        claims.remove(orderId);
        Seat seat = seats.get(claim.doctorId());
        if (seat == null) {
            return;
        }
        seat.load = Math.max(0, seat.load - 1);
        seat.lane = claim.lane();
        seat.score = claim.score();
        seat.specialty = specialty == null ? "" : specialty;
        seat.grade = grade;
    }

    @Override
    public synchronized void release(String doctorId, String specialty, int grade, long nowMillis) {
        Seat seat = touch(doctorId, specialty, grade);
        seat.load = Math.max(0, seat.load - 1);
        if (seat.offline || seat.load >= seat.limit) {
            seat.lane = -1;
            return;
        }
        seat.lane = Math.min(seat.load, 2);
        seat.score = nowMillis;
    }

    @Override
    public synchronized void online(String doctorId, String specialty, int grade, int inFlight, long nowMillis) {
        online(doctorId, specialty, grade, inFlight, 3, nowMillis);
    }

    @Override
    public synchronized void online(
            String doctorId, String specialty, int grade, int inFlight, int concurrentLimit, long nowMillis) {
        Seat seat = touch(doctorId, specialty, grade);
        seat.offline = false;
        seat.limit = Math.max(1, concurrentLimit);
        seat.load = Math.max(0, inFlight);
        if (seat.load >= seat.limit) {
            seat.lane = -1;
            return;
        }
        seat.lane = Math.min(seat.load, 2);
        seat.score = nowMillis;
    }

    @Override
    public synchronized void offline(String doctorId, String specialty, int grade) {
        Seat seat = touch(doctorId, specialty, grade);
        seat.offline = true;
        seat.lane = -1;
    }

    private void ensure(String specialty, int grade, List<DoctorSeat> allowed, long nowMillis) {
        if (allowed == null) {
            return;
        }
        for (DoctorSeat row : allowed) {
            if (row == null || row.doctorId() == null || row.doctorId().isBlank()) {
                continue;
            }
            Seat seat = seats.get(row.doctorId());
            if (seat == null) {
                seat = touch(row.doctorId(), specialty, grade);
                seat.limit = row.concurrentLimit();
                seat.load = Math.max(0, row.inFlight());
                if (seat.load >= seat.limit) {
                    seat.lane = -1;
                } else {
                    seat.lane = Math.min(seat.load, 2);
                    seat.score = row.idleSinceMillis() > 0 ? row.idleSinceMillis() : nowMillis;
                }
                continue;
            }
            seat.limit = row.concurrentLimit();
            if (seat.offline || seat.lane >= 0 || seat.load >= seat.limit) {
                continue;
            }
            seat.lane = seat.load;
            seat.score = nowMillis;
        }
    }

    private DoctorClaim pick(String specialty, int grade, List<DoctorSeat> allowed) {
        List<String> ids = new ArrayList<>();
        if (allowed != null) {
            for (DoctorSeat row : allowed) {
                if (row != null && row.doctorId() != null) {
                    ids.add(row.doctorId());
                }
            }
        }
        for (int lane = 0; lane <= 2; lane++) {
            String bestId = null;
            long bestScore = Long.MAX_VALUE;
            for (String id : ids) {
                Seat seat = seats.get(id);
                if (seat == null || seat.offline || seat.lane != lane) {
                    continue;
                }
                if (!specialty.equals(seat.specialty) || seat.grade != grade) {
                    continue;
                }
                if (bestId == null || seat.score < bestScore || (seat.score == bestScore && id.compareTo(bestId) < 0)) {
                    bestId = id;
                    bestScore = seat.score;
                }
            }
            if (bestId != null) {
                return new DoctorClaim(bestId, lane, bestScore);
            }
        }
        return null;
    }

    private Seat touch(String doctorId, String specialty, int grade) {
        Seat seat = seats.computeIfAbsent(doctorId, ignored -> new Seat());
        seat.specialty = specialty == null ? "" : specialty;
        seat.grade = grade;
        return seat;
    }

    private static final class Seat {
        private String specialty = "";
        private int grade;
        private int load;
        private int limit = 3;
        private int lane = -1;
        private long score;
        private boolean offline;
    }
}
