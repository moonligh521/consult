package com.moonligh.consult.agent.infrastructure.match;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.domain.MatchQueue;

class RedissonMatchBrokerTest {

    @Test
    void encodeRoundTripKeepsPayTimeAndGrade() {
        MatchQueue.Entry entry = new MatchQueue.Entry(
                "c1",
                "o1",
                Instant.parse("2026-10-27T11:00:00Z"),
                3,
                "呼吸内科",
                4);
        MatchQueue.Entry decoded = RedissonMatchBroker.decode(RedissonMatchBroker.encode(entry));
        assertEquals(entry.consultationId(), decoded.consultationId());
        assertEquals(entry.orderId(), decoded.orderId());
        assertEquals(entry.paidAt(), decoded.paidAt());
        assertEquals(entry.packageCode(), decoded.packageCode());
        assertEquals(entry.specialty(), decoded.specialty());
        assertEquals(entry.queuedGrade(), decoded.queuedGrade());
    }
}
