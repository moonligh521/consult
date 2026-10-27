package com.moonligh.consult.agent.api.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class DepartmentTryFromCodeTest {

    @Test
    void knownCodeStillResolves() {
        assertEquals(Department.INTERNAL, Department.tryFromCode(1));
        assertEquals(Department.INTERNAL, Department.fromCode(1));
    }

    @Test
    void unknownCodeDoesNotThrow() {
        assertNull(Department.tryFromCode(23));
        assertNull(Department.fromCode(23));
    }
}
