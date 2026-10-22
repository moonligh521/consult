package com.moonligh.consult.agent.infrastructure.persistence;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.ConsultationRepository;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.SessionAction;
import com.moonligh.consult.agent.domain.SessionWorkResult;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 咨询会话仓储。管事务和行锁，具体 SQL 交给 ConsultationMapper。
 */
@Repository
public class JdbcConsultationRepository implements ConsultationRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcConsultationRepository.class);

    /** 只负责增删改查。 */
    private final ConsultationMapper mapper;
    /** 一次请求包在一个事务里：锁、办事、保存一起提交或回滚。 */
    private final TransactionTemplate transactions;

    public JdbcConsultationRepository(ConsultationMapper mapper, TransactionTemplate transactions) {
        this.mapper = mapper;
        this.transactions = transactions;
    }

    /**
     * 加锁取出或新建会话，办完业务再保存。
     * 1104、1105 这类错误会先入库再抛，避免原话回滚丢失。
     */
    @Override
    public ConsultationRes runLocked(String consultationId, SessionAction action) {
        // 事务里不要直接抛业务异常，否则整笔会回滚。
        SessionWorkResult result = transactions.execute(status -> doInTransaction(status, consultationId, action));
        if (result.hasError()) {
            throw result.error();
        }
        return result.response();
    }

    /** 事务内顺序：建空行（如需要）→ 行锁读出 → 跑业务 → 写回。 */
    private SessionWorkResult doInTransaction(
            TransactionStatus status,
            String consultationId,
            SessionAction action) {
        // 没传编号就是新开一轮。
        boolean creating = consultationId == null || consultationId.isBlank();
        String id = creating ? UUID.randomUUID().toString() : consultationId;
        if (creating) {
            mapper.insertBlank(id);
        }
        // FOR UPDATE 锁住这一行，别的请求要等。
        ConsultationSession session = loadForUpdate(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        SessionWorkResult result = runAction(action, session);
        try {
            save(session);
            return result;
        } catch (DataAccessException error) {
            // 写库失败：普通请求回滚并报 1999。已急症时仍把安全画面还给前端。
            status.setRollbackOnly();
            log.error("persist failed id={} type={}", session.getId(), error.getClass().getName());
            if (session.isUrgentLatched() && !result.hasError() && result.response() != null) {
                return result;
            }
            return SessionWorkResult.ofError(new BusinessException(ErrorCode.UNKNOWN_ERROR));
        }
    }

    /** 行锁读出会话，再装上全部原话。 */
    private ConsultationSession loadForUpdate(String id) {
        ConsultationSession session = mapper.selectByIdForUpdate(id);
        if (session == null) {
            return null;
        }
        session.restoreMessages(mapper.selectMessages(id));
        session.restoreOrder(mapper.selectOrder(id));
        return session;
    }

    /** 业务异常先收进结果，让后面的 save 还能执行。 */
    private SessionWorkResult runAction(SessionAction action, ConsultationSession session) {
        try {
            return SessionWorkResult.ofResponse(action.run(session));
        } catch (BusinessException error) {
            return SessionWorkResult.ofError(error);
        }
    }

    /** 写回会话。原话和审计只追加本轮新增的。 */
    private void save(ConsultationSession session) {
        mapper.updateSession(session);
        for (ConsultationMessage message : session.newMessages()) {
            mapper.insertMessage(session.getId(), message);
        }
        session.markMessagesPersisted();
        for (SafetyDecisionAudit audit : session.drainPendingAudits()) {
            mapper.insertAudit(audit);
        }
        if (session.isOrderDirty() && session.getOrder() != null) {
            mapper.insertOrder(session.getId(), session.getOrder());
            session.markOrderPersisted();
        }
    }
}
