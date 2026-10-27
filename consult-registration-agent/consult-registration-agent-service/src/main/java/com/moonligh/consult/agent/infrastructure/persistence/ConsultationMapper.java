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
import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.domain.ConsultOrder;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.FallbackReason;
import com.moonligh.consult.agent.domain.safety.MessageSource;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.agent.domain.triage.MatchDecision;
import com.moonligh.consult.agent.domain.triage.TriageResult;

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
                 last_evaluated_seq, created_at, updated_at, is_deleted)
                VALUES (?, 1, '', false, false, false, false, false, 0, 0, ?, ?, 0)
                """,
                id, Timestamp.from(now), Timestamp.from(now));
    }

    /** 管理端最近咨询。不含卡片 JSON。 */
    public List<com.moonligh.consult.agent.api.res.AdminConsultationRes> listRecent() {
        return jdbc.query(
                """
                SELECT id, business_status, gender, age, patient_id, patient_name, duration, description,
                       DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
                       DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at
                FROM consultation_session
                WHERE is_deleted = 0
                ORDER BY updated_at DESC
                LIMIT 100
                """,
                (rs, rowNum) -> {
                    String description = rs.getString("description");
                    if (description != null && description.length() > 80) {
                        description = description.substring(0, 80);
                    }
                    int gender = rs.getInt("gender");
                    Integer genderValue = rs.wasNull() ? null : gender;
                    int age = rs.getInt("age");
                    Integer ageValue = rs.wasNull() ? null : age;
                    return new com.moonligh.consult.agent.api.res.AdminConsultationRes(
                            rs.getString("id"),
                            rs.getInt("business_status"),
                            genderValue,
                            ageValue,
                            rs.getString("patient_id"),
                            rs.getString("patient_name"),
                            rs.getString("duration"),
                            description,
                            rs.getString("created_at"),
                            rs.getString("updated_at"));
                });
    }

    /** 按编号查出会话，并加上行锁。找不到返回 null。 */
    public ConsultationSession selectByIdForUpdate(String id) {
        List<ConsultationSession> rows = jdbc.query(
                "SELECT * FROM consultation_session WHERE id = ? AND is_deleted = 0 FOR UPDATE",
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
                WHERE consultation_id = ? AND is_deleted = 0
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
                  business_status=?, service_type=?, gender=?, age=?, patient_id=?, patient_name=?, duration=?, description=?, card_json=?, triage_json=?,
                  urgent_latched=?, prompt_evaluation_latched=?, human_requested_latched=?, unsuitable_latched=?,
                  undetermined=?, fallback_reason=?, urgent_template_id=?, notice_template_id=?,
                  last_message_seq=?, last_evaluated_seq=?, updated_at=?
                WHERE id=? AND is_deleted=0
                """,
                session.getBusinessStatus().getCode(),
                null,
                session.getGender() == null ? null : session.getGender().getCode(),
                session.getAge(),
                session.getPatientId(),
                session.getPatientName(),
                session.getDuration(),
                session.getDescription(),
                toCardJson(session),
                toJson(session.getTriage(), "triage_json"),
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
                INSERT INTO consultation_message
                (consultation_id, seq, message_id, source, text, received_at, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                consultationId,
                message.seq(),
                message.messageId(),
                message.source().getCode(),
                message.text(),
                Timestamp.from(message.receivedAt()),
                Timestamp.from(message.receivedAt()),
                Timestamp.from(message.receivedAt()));
    }

    /** 写入一条安全审计。JSON 列只存编号和证据位置，不含原文。 */
    public void insertAudit(SafetyDecisionAudit audit) {
        try {
            jdbc.update(
                    """
                    INSERT INTO safety_decision_audit
                    (decision_id, consultation_id, message_seq, trigger_type, evaluation, certainty, matched_rule_ids,
                     shadow_rule_ids, model_trigger_ids, evidence_refs, catalog_version, rule_engine_version, model_status,
                     model_version, fallback_reason, previous_status, resulting_status, business_before,
                     business_after, template_id, decision_time, created_at, updated_at, is_deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    audit.decisionId(),
                    audit.consultationId(),
                    audit.messageSeq(),
                    audit.trigger().getCode(),
                    audit.evaluation().getCode(),
                    audit.ruleCertain() ? 1 : 2,
                    mapper.writeValueAsString(audit.matchedRuleIds()),
                    mapper.writeValueAsString(audit.shadowRuleIds()),
                    audit.modelTriggerIds().isEmpty() ? null : mapper.writeValueAsString(audit.modelTriggerIds()),
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
                    Timestamp.from(audit.decisionTime()),
                    Timestamp.from(audit.decisionTime()),
                    Timestamp.from(audit.decisionTime()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("audit_json");
        }
    }

    /** 批量写入逐节点决策。JSON 列只存输出和证据位置，不含原文。 */
    public void insertDecisions(List<MatchDecision> decisions) {
        if (decisions.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                """
                INSERT INTO consult_match_decision
                (decision_id, run_id, consultation_id, order_id, message_seq, trigger_type, node, certainty, source,
                 reason_code, output_json, evidence_refs, shadow_ids, model_status, model_cached, model_latency_ms,
                 latency_ms, catalog_version, prompt_version, model_name, input_fingerprint, decision_time,
                 created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                decisions,
                decisions.size(),
                (ps, row) -> {
                    Timestamp at = Timestamp.from(row.decisionTime());
                    ps.setString(1, row.decisionId());
                    ps.setString(2, row.runId());
                    ps.setString(3, row.consultationId());
                    ps.setString(4, row.orderId());
                    ps.setInt(5, row.messageSeq());
                    ps.setInt(6, row.triggerType());
                    ps.setInt(7, row.node().getCode());
                    ps.setInt(8, row.certainty().getCode());
                    ps.setInt(9, row.source().getCode());
                    ps.setString(10, truncate(row.reason(), 32));
                    ps.setString(11, toJson(row.output(), "output_json"));
                    ps.setString(12, toJson(row.evidence(), "evidence_refs"));
                    ps.setString(13, row.shadowIds().isEmpty() ? null : toJson(row.shadowIds(), "shadow_ids"));
                    ps.setObject(14, row.modelStatus() == null ? null : row.modelStatus().getCode());
                    ps.setObject(15, row.modelCached());
                    ps.setObject(16, row.modelLatencyMs() == null ? null : row.modelLatencyMs().intValue());
                    ps.setInt(17, (int) Math.min(Integer.MAX_VALUE, row.latencyMs()));
                    ps.setString(18, row.catalogVersion() == null ? "" : truncate(row.catalogVersion(), 32));
                    ps.setString(19, row.promptVersion());
                    ps.setString(20, row.modelName());
                    ps.setString(21, row.fingerprint() == null ? "" : row.fingerprint());
                    ps.setTimestamp(22, at);
                    ps.setTimestamp(23, at);
                    ps.setTimestamp(24, at);
                });
    }

    /** 管理端漏斗：按节点、来源、原因聚合。 */
    public List<com.moonligh.consult.agent.api.res.TriageFunnelRes> funnel(Instant from) {
        return jdbc.query(
                """
                SELECT node, source, reason_code, COUNT(*) AS total,
                       SUM(CASE WHEN model_status IS NOT NULL AND model_status <> 1 THEN 1 ELSE 0 END) AS model_calls,
                       COALESCE(AVG(model_latency_ms), 0) AS avg_latency
                FROM consult_match_decision
                WHERE decision_time >= ? AND is_deleted = 0
                GROUP BY node, source, reason_code
                ORDER BY node, total DESC
                """,
                (rs, rowNum) -> new com.moonligh.consult.agent.api.res.TriageFunnelRes(
                        rs.getInt("node"),
                        rs.getInt("source"),
                        rs.getString("reason_code"),
                        rs.getLong("total"),
                        rs.getLong("model_calls"),
                        Math.round(rs.getDouble("avg_latency"))),
                Timestamp.from(from));
    }

    /** 把会话表的一行填进内存对象。可空整型要先取值，再看 wasNull。 */
    private ConsultationSession mapSession(ResultSet rs, int rowNum) throws SQLException {
        ConsultationSession session = new ConsultationSession(rs.getString("id"));
        session.setBusinessStatus(ConsultationStatus.fromCode(rs.getInt("business_status")));
        int gender = rs.getInt("gender");
        if (!rs.wasNull()) {
            session.setGender(Gender.fromCode(gender));
        }
        int age = rs.getInt("age");
        session.setAge(rs.wasNull() ? null : age);
        session.setPatientId(rs.getString("patient_id"));
        session.setPatientName(rs.getString("patient_name"));
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
        String triageJson = rs.getString("triage_json");
        if (triageJson != null && !triageJson.isBlank()) {
            try {
                session.setTriage(mapper.readValue(triageJson, TriageResult.class));
            } catch (JsonProcessingException error) {
                // 老格式读不回就当没有，派单时按规则现算。
                session.setTriage(null);
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

    /** 查出这一轮未删除的订单。没有就返回 null。 */
    public ConsultOrder selectOrder(String consultationId) {
        List<ConsultOrder> rows = jdbc.query(
                """
                SELECT id, package_code, package_name, origin_amount, pay_amount, discount_amount, pay_status, paid_at,
                       doctor_id, doctor_name, doctor_title, hospital, department_code, specialty, skills_hit, schedule_id,
                       consult_minutes, ends_at, ended_at, end_reason
                FROM consult_order
                WHERE consultation_id = ? AND is_deleted = 0
                """,
                this::mapOrder,
                consultationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 插入一笔新订单。同一咨询只能有一行未删除订单。 */
    public void insertOrder(String consultationId, ConsultOrder order) {
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO consult_order
                (id, consultation_id, package_code, package_name, origin_amount, pay_amount, discount_amount,
                 pay_status, paid_at, doctor_id, doctor_name, doctor_title, hospital, department_code, specialty,
                 skills_hit, schedule_id, consult_minutes, ends_at, ended_at, end_reason, created_at, updated_at, is_deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE
                  package_code = VALUES(package_code),
                  package_name = VALUES(package_name),
                  origin_amount = VALUES(origin_amount),
                  pay_amount = VALUES(pay_amount),
                  discount_amount = VALUES(discount_amount),
                  pay_status = VALUES(pay_status),
                  paid_at = VALUES(paid_at),
                  doctor_id = VALUES(doctor_id),
                  doctor_name = VALUES(doctor_name),
                  doctor_title = VALUES(doctor_title),
                  hospital = VALUES(hospital),
                  department_code = VALUES(department_code),
                  specialty = VALUES(specialty),
                  skills_hit = VALUES(skills_hit),
                  schedule_id = VALUES(schedule_id),
                  consult_minutes = VALUES(consult_minutes),
                  ends_at = VALUES(ends_at),
                  ended_at = VALUES(ended_at),
                  end_reason = VALUES(end_reason),
                  updated_at = CURRENT_TIMESTAMP(3)
                """,
                order.orderId(),
                consultationId,
                order.packageCode(),
                order.packageName(),
                order.originAmount(),
                order.payAmount(),
                order.discountAmount(),
                order.payStatus().getCode(),
                Timestamp.from(order.paidAt()),
                order.doctor() == null ? null : order.doctor().id(),
                order.doctor() == null ? null : order.doctor().name(),
                order.doctor() == null ? null : order.doctor().title(),
                order.doctor() == null ? null : order.doctor().hospital(),
                order.doctor() == null ? null : order.doctor().departmentCode(),
                order.doctor() == null ? null : order.doctor().specialty(),
                order.doctor() == null ? null : blankToNull(order.doctor().evidence()),
                order.doctor() == null ? null : order.doctor().scheduleId(),
                order.consultMinutes(),
                order.endsAt() == null ? null : Timestamp.from(order.endsAt()),
                order.endedAt() == null ? null : Timestamp.from(order.endedAt()),
                order.endReason(),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private ConsultOrder mapOrder(ResultSet rs, int rowNum) throws SQLException {
        Timestamp paidAt = rs.getTimestamp("paid_at");
        String doctorId = rs.getString("doctor_id");
        AssignedDoctorRes doctor = doctorId == null || doctorId.isBlank()
                ? null
                : new AssignedDoctorRes(
                        doctorId,
                        rs.getString("doctor_name"),
                        rs.getString("doctor_title"),
                        rs.getString("hospital"),
                        rs.getInt("department_code"),
                        rs.getString("specialty"),
                        null,
                        rs.getString("skills_hit"),
                        rs.getString("schedule_id"));
        Timestamp endsAt = rs.getTimestamp("ends_at");
        Timestamp endedAt = rs.getTimestamp("ended_at");
        int minutes = rs.getInt("consult_minutes");
        if (rs.wasNull() || minutes <= 0) {
            minutes = 15;
        }
        return new ConsultOrder(
                rs.getString("id"),
                rs.getInt("package_code"),
                rs.getString("package_name"),
                rs.getBigDecimal("origin_amount"),
                rs.getBigDecimal("pay_amount"),
                rs.getBigDecimal("discount_amount"),
                PayStatus.fromCode(rs.getInt("pay_status")),
                paidAt == null ? null : paidAt.toInstant(),
                doctor,
                minutes,
                endsAt == null ? null : endsAt.toInstant(),
                endedAt == null ? null : endedAt.toInstant(),
                rs.getString("end_reason"));
    }

    private String toJson(Object value, String column) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException(column);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
