package com.moonligh.consult.agent.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.ServiceType;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.ConsultationRepository;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.SessionAction;
import com.moonligh.consult.agent.domain.SessionWorkResult;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.FallbackReason;
import com.moonligh.consult.agent.domain.safety.MessageSource;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 咨询会话的 MySQL 持久化。同一咨询使用行锁串行处理。
 */
@Component
public class JdbcConsultationRepository implements ConsultationRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcConsultationRepository.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;

    public JdbcConsultationRepository(JdbcTemplate jdbc, TransactionTemplate transactions, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.mapper = mapper;
    }

    /**
     * 加锁加载或创建会话，执行业务后保存。
     * 业务异常在事务提交后再抛出，保证原话已落库。
     */
    @Override
    public ConsultationRes runLocked(String consultationId, SessionAction action) {
        SessionWorkResult result = transactions.execute(status -> doInTransaction(status, consultationId, action));
        if (result.hasError()) {
            throw result.error();
        }
        return result.response();
    }

    private SessionWorkResult doInTransaction(
            TransactionStatus status,
            String consultationId,
            SessionAction action) {
        boolean creating = consultationId == null || consultationId.isBlank();
        String id = creating ? UUID.randomUUID().toString() : consultationId;
        if (creating) {
            insertBlank(id);
        }
        ConsultationSession session = loadForUpdate(id);
        if (session == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        SessionWorkResult result = runAction(action, session);
        try {
            save(session);
            return result;
        } catch (DataAccessException error) {
            // 持久化失败回滚；已锁存急症时仍返回安全评估结果。
            status.setRollbackOnly();
            log.error("persist failed id={} type={}", session.getId(), error.getClass().getName());
            if (session.isUrgentLatched() && !result.hasError() && result.response() != null) {
                return result;
            }
            return SessionWorkResult.ofError(new BusinessException(ErrorCode.UNKNOWN_ERROR));
        }
    }

    private SessionWorkResult runAction(SessionAction action, ConsultationSession session) {
        try {
            return SessionWorkResult.ofResponse(action.run(session));
        } catch (BusinessException error) {
            return SessionWorkResult.ofError(error);
        }
    }

    private void insertBlank(String id) {
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO consultation_session
                (id, business_status, description, urgent_latched, prompt_evaluation_latched,
                 human_requested_latched, unsuitable_latched, undetermined, last_message_seq,
                 last_evaluated_seq, created_at, updated_at)
                VALUES (?, 1, '', false, false, false, false, false, 0, 0, ?, ?)
                """,
                id, Timestamp.from(now), Timestamp.from(now));
    }

    private ConsultationSession loadForUpdate(String id) {
        List<ConsultationSession> rows = jdbc.query(
                "SELECT * FROM consultation_session WHERE id = ? FOR UPDATE",
                this::mapSession,
                id);
        if (rows.isEmpty()) {
            return null;
        }
        ConsultationSession session = rows.get(0);
        List<ConsultationMessage> messages = jdbc.query(
                "SELECT seq, message_id, source, text, received_at FROM consultation_message WHERE consultation_id = ? ORDER BY seq",
                (rs, rowNum) -> new ConsultationMessage(
                        rs.getInt("seq"),
                        rs.getString("message_id"),
                        MessageSource.fromCode(rs.getInt("source")),
                        rs.getString("text"),
                        rs.getTimestamp("received_at").toInstant()),
                id);
        session.restoreMessages(messages);
        return session;
    }

    private ConsultationSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        ConsultationSession session = new ConsultationSession(rs.getString("id"));
        session.setBusinessStatus(ConsultationStatus.fromCode(rs.getInt("business_status")));
        int serviceType = rs.getInt("service_type");
        if (!rs.wasNull()) {
            session.setServiceType(ServiceType.fromCode(serviceType));
        }
        int gender = rs.getInt("gender");
        if (!rs.wasNull()) {
            session.setGender(Gender.fromCode(gender));
        }
        int age = rs.getInt("age");
        session.setAge(rs.wasNull() ? null : age);
        session.setDuration(rs.getString("duration"));
        session.setDescription(rs.getString("description"));
        String cardJson = rs.getString("card_json");
        if (cardJson != null && !cardJson.isBlank()) {
            try {
                session.setCard(mapper.readValue(cardJson, ConsultationCardRes.class));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("card_json");
            }
        }
        session.setUrgentLatched(rs.getBoolean("urgent_latched"));
        session.setPromptEvaluationLatched(rs.getBoolean("prompt_evaluation_latched"));
        session.setHumanRequestedLatched(rs.getBoolean("human_requested_latched"));
        session.setUnsuitableLatched(rs.getBoolean("unsuitable_latched"));
        session.setUndetermined(rs.getBoolean("undetermined"));
        String fallback = rs.getString("fallback_reason");
        if (fallback != null) {
            session.setFallbackReason(FallbackReason.valueOf(fallback));
        }
        session.setUrgentTemplateId(rs.getString("urgent_template_id"));
        session.setNoticeTemplateId(rs.getString("notice_template_id"));
        session.setLastMessageSeq(rs.getInt("last_message_seq"));
        session.setLastEvaluatedSeq(rs.getInt("last_evaluated_seq"));
        session.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        session.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return session;
    }

    private void save(ConsultationSession session) {
        String cardJson;
        try {
            cardJson = session.getCard() == null ? null : mapper.writeValueAsString(session.getCard());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("card_json");
        }
        jdbc.update(
                """
                UPDATE consultation_session SET
                  business_status=?, service_type=?, gender=?, age=?, duration=?, description=?, card_json=?,
                  urgent_latched=?, prompt_evaluation_latched=?, human_requested_latched=?, unsuitable_latched=?,
                  undetermined=?, fallback_reason=?, urgent_template_id=?, notice_template_id=?,
                  last_message_seq=?, last_evaluated_seq=?, updated_at=?
                WHERE id=?
                """,
                session.getBusinessStatus().getCode(),
                session.getServiceType() == null ? null : session.getServiceType().getCode(),
                session.getGender() == null ? null : session.getGender().getCode(),
                session.getAge(),
                session.getDuration(),
                session.getDescription(),
                cardJson,
                session.isUrgentLatched(),
                session.isPromptEvaluationLatched(),
                session.isHumanRequestedLatched(),
                session.isUnsuitableLatched(),
                session.isUndetermined(),
                session.getFallbackReason() == null ? null : session.getFallbackReason().name(),
                session.getUrgentTemplateId(),
                session.getNoticeTemplateId(),
                session.getLastMessageSeq(),
                session.getLastEvaluatedSeq(),
                Timestamp.from(Instant.now()),
                session.getId());
        for (ConsultationMessage message : session.newMessages()) {
            jdbc.update(
                    """
                    INSERT INTO consultation_message (consultation_id, seq, message_id, source, text, received_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    session.getId(),
                    message.seq(),
                    message.messageId(),
                    message.source().getCode(),
                    message.text(),
                    Timestamp.from(message.receivedAt()));
        }
        session.markMessagesPersisted();
        for (SafetyDecisionAudit audit : session.drainPendingAudits()) {
            insertAudit(audit);
        }
    }

    private void insertAudit(SafetyDecisionAudit audit) {
        try {
            jdbc.update(
                    """
                    INSERT INTO safety_decision_audit
                    (decision_id, consultation_id, message_seq, trigger_type, evaluation, matched_rule_ids,
                     shadow_rule_ids, evidence_refs, catalog_version, rule_engine_version, model_status,
                     model_version, fallback_reason, previous_status, resulting_status, business_before,
                     business_after, template_id, decision_time)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    audit.decisionId(),
                    audit.consultationId(),
                    audit.messageSeq(),
                    audit.trigger().getCode(),
                    audit.evaluation().getCode(),
                    mapper.writeValueAsString(audit.matchedRuleIds()),
                    mapper.writeValueAsString(audit.shadowRuleIds()),
                    mapper.writeValueAsString(audit.evidence()),
                    audit.catalogVersion(),
                    audit.ruleEngineVersion(),
                    audit.modelStatus().getCode(),
                    audit.modelVersion(),
                    audit.fallbackReason() == null ? null : audit.fallbackReason().name(),
                    audit.previousStatus(),
                    audit.resultingStatus(),
                    audit.businessBefore(),
                    audit.businessAfter(),
                    audit.templateId(),
                    Timestamp.from(audit.decisionTime()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("audit_json");
        }
    }
}
