package com.moonligh.consult.agent.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.ServiceType;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.FallbackReason;
import com.moonligh.consult.agent.domain.safety.MessageSource;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;

/**
 * 咨询相关 SQL。这里不管事务，事务由仓储负责。
 */
@Repository
public class ConsultationMapper {

    /** 执行 SQL。 */
    private final JdbcTemplate jdbc;
    /** 卡片和审计里的 JSON 字段用它序列化。 */
    private final ObjectMapper mapper;

    public ConsultationMapper(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /** 插入一行空白会话。业务状态 1 是待描述。 */
    public void insertBlank(String id) {
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

    /** 按编号查出会话，并加上行锁。找不到返回 null。 */
    public ConsultationSession selectByIdForUpdate(String id) {
        List<ConsultationSession> rows = jdbc.query(
                "SELECT * FROM consultation_session WHERE id = ? FOR UPDATE",
                this::mapSession,
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按序号查出这一轮的全部原话。 */
    public List<ConsultationMessage> selectMessages(String id) {
        return jdbc.query(
                """
                SELECT seq, message_id, source, text, received_at
                FROM consultation_message
                WHERE consultation_id = ?
                ORDER BY seq
                """,
                (rs, rowNum) -> new ConsultationMessage(
                        rs.getInt("seq"),
                        rs.getString("message_id"),
                        MessageSource.fromCode(rs.getInt("source")),
                        rs.getString("text"),
                        rs.getTimestamp("received_at").toInstant()),
                id);
    }

    /** 整行更新会话。原话不在这里改。 */
    public void updateSession(ConsultationSession session) {
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
                toCardJson(session),
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
    }

    /** 追加一条原话。旧行不更新。 */
    public void insertMessage(String consultationId, ConsultationMessage message) {
        jdbc.update(
                """
                INSERT INTO consultation_message (consultation_id, seq, message_id, source, text, received_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                consultationId,
                message.seq(),
                message.messageId(),
                message.source().getCode(),
                message.text(),
                Timestamp.from(message.receivedAt()));
    }

    /** 写入一条安全审计。JSON 列只存编号和证据位置，不含原文。 */
    public void insertAudit(SafetyDecisionAudit audit) {
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

    /** 把会话表的一行填进内存对象。可空整型要先取值，再看 wasNull。 */
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
        // 五个锁存位从库里原样装回，只升不降。
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

    /** 卡片转成 JSON 存库。没有卡片就存 null。 */
    private String toCardJson(ConsultationSession session) {
        if (session.getCard() == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(session.getCard());
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("card_json");
        }
    }
}
