package com.moonligh.consult.agent.domain;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.safety.InMemorySafetyAuditRecorder;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 会话仓储的进程内实现，供单元测试使用。
 */
public class InMemoryConsultationRepository implements ConsultationRepository {

    /** 测试里的会话表。 */
    private final ConcurrentMap<String, ConsultationSession> sessions;
    /** 同一咨询编号排队，模拟生产行锁。 */
    private final ConcurrentMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();
    private final InMemorySafetyAuditRecorder auditRecorder;
    /** 下一次保存故意失败，用来测急症落库保护。 */
    private boolean failNextPersist;

    public InMemoryConsultationRepository() {
        this(new ConcurrentHashMap<>(), new InMemorySafetyAuditRecorder());
    }

    public InMemoryConsultationRepository(
            ConcurrentMap<String, ConsultationSession> sessions,
            InMemorySafetyAuditRecorder auditRecorder) {
        this.sessions = sessions;
        this.auditRecorder = auditRecorder;
    }

    /** 下一轮 save 假装数据库挂了。 */
    public void failNextPersist() {
        this.failNextPersist = true;
    }

    /** 测试里直接取出会话。找不到抛 1101。 */
    public ConsultationSession getRequired(String consultationId) {
        ConsultationSession session = sessions.get(consultationId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        return session;
    }

    /**
     * 加锁加载或创建会话，执行业务后保存。
     * 业务异常在保存后再抛出，保证原话已写入。
     */
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
            // 先保存，再抛 1104、1105，避免原话丢失。
            if (result.hasError()) {
                throw result.error();
            }
            return result.response();
        } finally {
            lock.unlock();
        }
    }

    /** 新开一轮就新建；已有编号找不到就 1101。 */
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

    /** 业务异常先收进结果，让后面的 persist 还能执行。 */
    private SessionWorkResult runAction(SessionAction action, ConsultationSession session) {
        try {
            return SessionWorkResult.ofResponse(action.run(session));
        } catch (BusinessException error) {
            return SessionWorkResult.ofError(error);
        }
    }

    /** 写入内存表。故意失败时：已急症仍保留画面，其它请求报 1999。 */
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
