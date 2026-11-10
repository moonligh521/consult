package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class MemoryOpeningClaimTest {

    @Test
    void secondClaimInsideTheWindowKeepsTheFirstConsultation() {
        MemoryOpeningClaim claims = new MemoryOpeningClaim();
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        assertEquals("c-1", claims.claim("acct", "干咳", "c-1", now));
        assertEquals("c-1", claims.claim("acct", "干咳", "c-2", now.plusSeconds(3)));
    }

    @Test
    void claimAfterTheWindowTakesOver() {
        MemoryOpeningClaim claims = new MemoryOpeningClaim();
        Instant now = Instant.parse("2026-11-04T12:00:00Z");
        claims.claim("acct", "干咳", "c-1", now);
        assertEquals("c-2", claims.claim("acct", "干咳", "c-2", now.plusSeconds(9)));
    }
}
