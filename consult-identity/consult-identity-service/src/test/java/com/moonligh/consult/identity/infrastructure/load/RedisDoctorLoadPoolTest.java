package com.moonligh.consult.identity.infrastructure.load;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.identity.domain.load.DoctorClaim;

class RedisDoctorLoadPoolTest {

    @Test
    void keysSeparateSpecialtyAndGrade() {
        assertEquals(
                "consult:load:呼吸内科:2:0",
                RedisDoctorLoadPool.keys("o1", "呼吸内科", 2).get(2));
        assertEquals(
                "consult:load:claim:o1",
                RedisDoctorLoadPool.keys("o1", "呼吸内科", 2).get(0));
    }

    @Test
    void parseClaim() {
        DoctorClaim claim = RedisDoctorLoadPool.parse("doc-a|0|100");
        assertEquals("doc-a", claim.doctorId());
        assertEquals(0, claim.lane());
        assertEquals(100, claim.score());
        assertNull(RedisDoctorLoadPool.parse(""));
    }
}
