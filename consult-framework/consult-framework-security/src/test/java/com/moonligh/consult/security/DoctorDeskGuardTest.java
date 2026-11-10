package com.moonligh.consult.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

class DoctorDeskGuardTest {

    @Test
    void blankBindingIsRejected() {
        BusinessException error = assertThrows(BusinessException.class, () -> DoctorDeskGuard.required(" "));
        assertEquals(ErrorCode.DOCTOR_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void anotherDoctorIsRejected() {
        BusinessException error = assertThrows(BusinessException.class, () -> DoctorDeskGuard.same("doc-1", "doc-2"));
        assertEquals(ErrorCode.DOCTOR_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void theBoundDoctorPasses() {
        DoctorDeskGuard.same(" doc-1 ", "doc-1");
    }
}
