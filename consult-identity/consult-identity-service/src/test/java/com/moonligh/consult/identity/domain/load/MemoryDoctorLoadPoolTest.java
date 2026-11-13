package com.moonligh.consult.identity.domain.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class MemoryDoctorLoadPoolTest {

    private final MemoryDoctorLoadPool pool = new MemoryDoctorLoadPool();

    @Test
    void idleLongerDoctorIsFirst() {
        DoctorClaim claim = pool.claim("o1", "呼吸内科", 2, List.of(
                new DoctorSeat("late", 0, 200),
                new DoctorSeat("early", 0, 100)), 1_000);
        assertEquals("early", claim.doctorId());
    }

    @Test
    void emptyDoctorBeatsDoctorAlreadyHoldingOne() {
        pool.online("busy", "呼吸内科", 2, 1, 50);
        pool.online("free", "呼吸内科", 2, 0, 80);
        DoctorClaim claim = pool.claim("o1", "呼吸内科", 2, List.of(
                new DoctorSeat("busy", 1, 50),
                new DoctorSeat("free", 0, 80)), 90);
        assertEquals("free", claim.doctorId());
    }

    @Test
    void releaseJoinsTheTail() {
        pool.claim("o1", "呼吸内科", 2, List.of(
                new DoctorSeat("a", 0, 100),
                new DoctorSeat("b", 0, 200)), 300);
        pool.release("a", "呼吸内科", 2, 400);
        DoctorClaim next = pool.claim("o2", "呼吸内科", 2, List.of(
                new DoctorSeat("a", 0, 100),
                new DoctorSeat("b", 0, 200)), 500);
        assertEquals("b", next.doctorId());
    }

    @Test
    void thirdOrderLeavesTheQueues() {
        pool.online("a", "呼吸内科", 2, 2, 10);
        DoctorClaim claim = pool.claim("o1", "呼吸内科", 2, List.of(new DoctorSeat("a", 2, 10)), 20);
        assertEquals("a", claim.doctorId());
        assertNull(pool.claim("o2", "呼吸内科", 2, List.of(new DoctorSeat("a", 2, 10)), 30));
    }

    @Test
    void limitAboveThreeStillAcceptsAnotherOrder() {
        DoctorClaim claim = pool.claim(
                "o4", "呼吸内科", 2, List.of(new DoctorSeat("a", 3, 10, 5)), 40);
        assertEquals("a", claim.doctorId());
    }

    @Test
    void sameOrderDoesNotIncrementTwice() {
        DoctorClaim first = pool.claim("o1", "呼吸内科", 2, List.of(new DoctorSeat("a", 0, 10)), 20);
        DoctorClaim again = pool.claim("o1", "呼吸内科", 2, List.of(new DoctorSeat("a", 0, 10)), 30);
        assertEquals(first, again);
        pool.release("a", "呼吸内科", 2, 40);
        DoctorClaim other = pool.claim("o2", "呼吸内科", 2, List.of(new DoctorSeat("a", 0, 10)), 50);
        assertEquals("a", other.doctorId());
        assertEquals(0, other.lane());
    }

    @Test
    void restoreKeepsOriginalPlace() {
        DoctorClaim claim = pool.claim("o1", "呼吸内科", 2, List.of(
                new DoctorSeat("a", 0, 100),
                new DoctorSeat("b", 0, 200)), 300);
        pool.restore("o1", claim, "呼吸内科", 2);
        DoctorClaim again = pool.claim("o2", "呼吸内科", 2, List.of(
                new DoctorSeat("a", 0, 100),
                new DoctorSeat("b", 0, 200)), 400);
        assertEquals("a", again.doctorId());
        assertEquals(100, again.score());
    }

    @Test
    void offlineDoctorIsSkipped() {
        pool.online("a", "呼吸内科", 2, 0, 10);
        pool.offline("a", "呼吸内科", 2);
        assertNull(pool.claim("o1", "呼吸内科", 2, List.of(new DoctorSeat("a", 0, 10)), 20));
    }
}
