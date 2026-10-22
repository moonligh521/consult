package com.moonligh.consult.agent.domain;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.safety.InMemorySafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.SafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 把会话放在内存里。测试用；同一编号加锁，效果和数据库行锁一样。
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

    public InMemoryConsultationRepository(
            ConcurrentMap<String, ConsultationSession> sessions,
            SafetyAuditRecorder auditRecorder) {
        this.sessions = sessions;
        this.auditRecorder = auditRecorder;
    }

    public void failNextPersist() {
        this.failNextPersist = true;
    }

    /**
     * 测试里查看已经保存的会话。请求结束后再调用，不再持锁。
     */
    public ConsultationSession getRequired(String consultationId) {
        ConsultationSession session = sessions.get(consultationId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        return session;
    }

    @Override
    public ConsultationRes runLocked(String consultationId, SessionAction action) {
        boolean creating = consultationId == null || consultationId.isBlank();
        String id = creating ? UUID.randomUUID().toString() : consultationId;
        ReentrantLock lock = locks.computeIfAbsent(id, key -> new ReentrantLock());
        lock.lock();
        try {
            ConsultationSession session = loadOrCreate(id, creating);
            SessionWorkResult result = runAction(action, session);
            persist(session);
            if (result.hasError()) {
                throw result.error();
            }
            return result.response();
        } finally {
            lock.unlock();
        }
    }

    private ConsultationSession loadOrCreate(String id, boolean creating) {
        if (creating) {
            ConsultationSession session = new ConsultationSession(id);
            sessions.put(id, session);
            return session;
        }
        ConsultationSession session = sessions.get(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        return session;
    }

    private SessionWorkResult runAction(SessionAction action, ConsultationSession session) {
        try {
            return SessionWorkResult.ofResponse(action.run(session));
        } catch (BusinessException error) {
            return SessionWorkResult.ofError(error);
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
