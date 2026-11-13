package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

class InMemoryConsultationRepositoryTest {

    @Test
    void reserveNewDoesNotPersistUntilLockedCommit() {
        InMemoryConsultationRepository repository = new InMemoryConsultationRepository();
        String id = repository.reserveNew();
        assertNotNull(id);
        assertNull(repository.get(id));

        repository.runLocked(id, session -> {
            assertEquals(id, session.getId());
            return null;
        });
        assertNotNull(repository.get(id));
    }

    @Test
    void unknownIdStillNotFound() {
        InMemoryConsultationRepository repository = new InMemoryConsultationRepository();
        BusinessException error = assertThrows(BusinessException.class,
                () -> repository.runLocked("missing-id", session -> null));
        assertEquals(ErrorCode.CONSULTATION_NOT_FOUND, error.getErrorCode());
    }
}
