package com.moonligh.consult.agent.domain;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.domain.safety.InMemorySafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.SafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 进程内仓储。验收测试使用；按会话编号加锁，语义与库实现一致。
 */
@Component
@ConditionalOnMissingBean(DataSource.class)
public class InMemoryConsultationRepository implements ConsultationRepository {

    private final ConcurrentMap<String, ConsultationSession> sessions;
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final SafetyAuditRecorder auditRecorder;
    private boolean failNextPersist;

    public InMemoryConsultationRepository() {
        this(new ConcurrentHashMap<>(), new InMemorySafetyAuditRecorder());
    }

    public InMemoryConsultationRepository(ConcurrentMap<String, ConsultationSession> sessions, SafetyAuditRecorder auditRecorder) {
        this.sessions = sessions;
        this.auditRecorder = auditRecorder;
    }

    public void failNextPersist() {
        this.failNextPersist = true;
    }

    @Override
    public <T> T inLock(String consultationId, Function<ConsultationSession, T> work) {
        boolean creating = consultationId == null || consultationId.isBlank();
        String id = creating ? UUID.randomUUID().toString() : consultationId;
        ReentrantLock lock = locks.computeIfAbsent(id, key -> new ReentrantLock());
        lock.lock();
        try {
            ConsultationSession session;
            if (creating) {
                session = new ConsultationSession(id);
                sessions.put(id, session);
            } else {
                session = sessions.get(id);
                if (session == null) {
                    throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
                }
            }
            BusinessException failure = null;
            T result = null;
            try {
                result = work.apply(session);
            } catch (BusinessException error) {
                failure = error;
            }
            persist(session);
            if (failure != null) {
                throw failure;
            }
            return result;
        } finally {
            lock.unlock();
        }
    }

    private void persist(ConsultationSession session) {
        if (failNextPersist) {
            failNextPersist = false;
            if (session.isUrgentLatched()) {
                return;
            }
            throw new BusinessException(ErrorCode.UNKNOWN_ERROR);
        }
        sessions.put(session.getId(), session);
        session.markMessagesPersisted();
        for (SafetyDecisionAudit audit : session.drainPendingAudits()) {
            auditRecorder.record(audit);
        }
    }
}
