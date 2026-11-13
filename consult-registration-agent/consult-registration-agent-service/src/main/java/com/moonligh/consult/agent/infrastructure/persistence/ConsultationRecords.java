package com.moonligh.consult.agent.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.AuditInsert;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.BenefitRow;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.DecisionInsert;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.FunnelRow;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.MessageRow;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.OrderInsert;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.OrderRow;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.QueueRow;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.SessionRow;
import com.moonligh.consult.agent.infrastructure.persistence.ConsultationRecordMapper.SessionUpdate;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.AdminConsultationRes;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRecordRes;
import com.moonligh.consult.agent.api.res.TriageFunnelRes;
import com.moonligh.consult.agent.domain.BenefitLedgerRow;
import com.moonligh.consult.agent.domain.ConsultOrder;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.MatchQueue;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.FallbackReason;
import com.moonligh.consult.agent.domain.safety.MessageSource;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.agent.domain.triage.MatchDecision;
import com.moonligh.consult.agent.domain.triage.TriageResult;

/**
 * 咨询持久化。不管事务，事务由仓储负责。语句在 ConsultationRecordMapper.xml。
 */
@Repository
public class ConsultationRecords {

    private final ConsultationRecordMapper records;
    /** 卡片和审计里的 JSON 字段用它序列化。 */
    private final ObjectMapper mapper;

    public ConsultationRecords(ConsultationRecordMapper records, ObjectMapper mapper) {
        this.records = records;
        this.mapper = mapper;
    }

    /** 插入一行空白会话。业务状态 1 是待描述。 */
    public void insertBlank(String id) {
        records.insertBlank(id, Instant.now());
    }

    /** 管理端时间窗内最近咨询，走 idx_consult_session_updated。不含卡片 JSON。 */
    public List<AdminConsultationRes> listRecent(Instant from, Instant to, int limit) {
        List<AdminConsultationRes> rows = records.listRecent(from, to, limit);
        List<AdminConsultationRes> out = new ArrayList<>(rows.size());
        for (AdminConsultationRes row : rows) {
            String description = row.description();
            if (description != null && description.length() > 80) {
                description = description.substring(0, 80);
            }
            out.add(new AdminConsultationRes(
                    row.id(),
                    row.status(),
                    row.gender(),
                    row.age(),
                    row.patientId(),
                    row.patientName(),
                    row.duration(),
                    description,
                    row.createdAt(),
                    row.updatedAt()));
        }
        return out;
    }

    public boolean consultationExists(String consultationId) {
        Integer count = records.countSession(consultationId);
        return count != null && count > 0;
    }

    public LatestMatch latestMatch(String consultationId) {
        return records.latestMatch(consultationId);
    }

    public record LatestMatch(String runId, Instant decisionTime) {
    }

    /** 按编号查出会话，并加上行锁。不读诊室快照；没改快照时保存也不回写它。找不到返回 null。 */
    public ConsultationSession selectByIdForUpdate(String id) {
        SessionRow row = records.selectByIdForUpdate(id);
        return row == null ? null : mapSession(row, false);
    }

    /** 按编号读会话，不加锁，带诊室快照。 */
    public ConsultationSession selectById(String id) {
        SessionRow row = records.selectById(id);
        return row == null ? null : mapSession(row, true);
    }

    /** 当前账号一年窗口内的记录，按最近更新倒序。 */
    public List<ConsultationRecordRes> listMine(String accountId, Instant from, int offset, int limit) {
        return records.listMine(accountId, from, limit, offset);
    }

    public long countMine(String accountId, Instant from) {
        Long total = records.countMine(accountId, from);
        return total == null ? 0L : total;
    }

    /** 账号在 since 之后发过同样原话的那一轮。没有则 null。 */
    public String findRecentOpening(String accountId, String text, Instant since) {
        if (accountId == null || text == null || since == null) {
            return null;
        }
        List<String> ids = records.findRecentOpening(accountId, text, since);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** 按序号查出这一轮的全部原话。 */
    public List<ConsultationMessage> selectMessages(String id) {
        List<MessageRow> rows = records.selectMessages(id);
        List<ConsultationMessage> messages = new ArrayList<>(rows.size());
        for (MessageRow row : rows) {
            messages.add(new ConsultationMessage(
                    nz(row.seq()),
                    row.messageId(),
                    MessageSource.fromCode(nz(row.source())),
                    row.text(),
                    row.receivedAt()));
        }
        return messages;
    }

    /** 更新会话。诊室快照只在本次改过时回写，平时不重写这个大字段。原话不在这里改。 */
    public void updateSession(ConsultationSession session) {
        SessionUpdate row = new SessionUpdate(
                session.getBusinessStatus().getCode(),
                session.getGender() == null ? null : session.getGender().getCode(),
                session.getAge(),
                session.getPatientId(),
                session.getPatientName(),
                session.getAccountId(),
                session.getTitle(),
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
                Instant.now(),
                session.isTranscriptDirty() ? session.getTranscript() : null,
                session.getId());
        if (session.isTranscriptDirty()) {
            records.updateSessionTranscript(row);
        } else {
            records.updateSession(row);
        }
    }

    /** 追加一条原话。旧行不更新。 */
    public void insertMessage(String consultationId, ConsultationMessage message) {
        records.insertMessage(
                consultationId,
                message.seq(),
                message.messageId(),
                message.source().getCode(),
                message.text(),
                message.receivedAt());
    }

    /** 写入一条安全审计。JSON 列只存编号和证据位置，不含原文。 */
    public void insertAudit(SafetyDecisionAudit audit) {
        try {
            records.insertAudit(new AuditInsert(
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
                    audit.decisionTime()));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("audit_json");
        }
    }

    /** 批量写入逐节点决策。JSON 列只存输出和证据位置，不含原文。 */
    public void insertDecisions(List<MatchDecision> decisions) {
        if (decisions.isEmpty()) {
            return;
        }
        for (MatchDecision row : decisions) {
            records.insertDecision(new DecisionInsert(
                    row.decisionId(),
                    row.runId(),
                    row.consultationId(),
                    row.orderId(),
                    row.messageSeq(),
                    row.triggerType(),
                    row.node().getCode(),
                    row.certainty().getCode(),
                    row.source().getCode(),
                    truncate(row.reason(), 32),
                    toJson(row.output(), "output_json"),
                    toJson(row.evidence(), "evidence_refs"),
                    row.shadowIds().isEmpty() ? null : toJson(row.shadowIds(), "shadow_ids"),
                    row.modelStatus() == null ? null : row.modelStatus().getCode(),
                    row.modelCached(),
                    row.modelLatencyMs() == null ? null : row.modelLatencyMs().intValue(),
                    (int) Math.min(Integer.MAX_VALUE, row.latencyMs()),
                    row.catalogVersion() == null ? "" : truncate(row.catalogVersion(), 32),
                    row.promptVersion(),
                    row.modelName(),
                    row.fingerprint() == null ? "" : row.fingerprint(),
                    row.decisionTime()));
        }
    }

    /** 管理端漏斗：按节点、来源、原因聚合。 */
    public List<TriageFunnelRes> funnel(Instant from) {
        List<FunnelRow> rows = records.funnel(from);
        List<TriageFunnelRes> out = new ArrayList<>(rows.size());
        for (FunnelRow row : rows) {
            out.add(new TriageFunnelRes(
                    row.node(),
                    row.source(),
                    row.reasonCode(),
                    row.total(),
                    row.modelCalls(),
                    Math.round(row.avgLatency())));
        }
        return out;
    }

    /** 把会话表的一行填进内存对象。可空整型要先取值，再看 wasNull。 */
    private ConsultationSession mapSession(SessionRow row, boolean withTranscript) {
        ConsultationSession session = new ConsultationSession(row.id);
        session.setBusinessStatus(ConsultationStatus.fromCode(nz(row.businessStatus)));
        if (row.gender != null) {
            session.setGender(Gender.fromCode(row.gender));
        }
        session.setAge(row.age);
        session.setPatientId(row.patientId);
        session.setPatientName(row.patientName);
        session.setAccountId(row.accountId);
        session.setTitle(row.title);
        if (withTranscript) {
            session.restoreTranscript(row.transcriptJson);
        }
        session.setDuration(row.duration);
        session.setDescription(row.description);
        String cardJson = row.cardJson;
        if (cardJson != null && !cardJson.isBlank()) {
            try {
                session.setCard(mapper.readValue(cardJson, ConsultationCardRes.class));
            } catch (JsonProcessingException error) {
                throw new IllegalStateException("card_json");
            }
        }
        String triageJson = row.triageJson;
        if (triageJson != null && !triageJson.isBlank()) {
            try {
                session.setTriage(mapper.readValue(triageJson, TriageResult.class));
            } catch (JsonProcessingException error) {
                // 老格式读不回就当没有，派单时按规则现算。
                session.setTriage(null);
            }
        }
        // 五个锁存位从库里原样装回，只升不降。
        session.setUrgentLatched(Boolean.TRUE.equals(row.urgentLatched));
        session.setPromptEvaluationLatched(Boolean.TRUE.equals(row.promptEvaluationLatched));
        session.setHumanRequestedLatched(Boolean.TRUE.equals(row.humanRequestedLatched));
        session.setUnsuitableLatched(Boolean.TRUE.equals(row.unsuitableLatched));
        session.setUndetermined(Boolean.TRUE.equals(row.undetermined));
        if (row.fallbackReason != null) {
            session.setFallbackReason(FallbackReason.valueOf(row.fallbackReason));
        }
        session.setUrgentTemplateId(row.urgentTemplateId);
        session.setNoticeTemplateId(row.noticeTemplateId);
        session.setLastMessageSeq(nz(row.lastMessageSeq));
        session.setLastEvaluatedSeq(nz(row.lastEvaluatedSeq));
        session.setCreatedAt(row.createdAt);
        session.setUpdatedAt(row.updatedAt);
        return session;
    }

    /** 查出这一轮未删除的订单。没有就返回 null。 */
    public ConsultOrder selectOrder(String consultationId) {
        List<OrderRow> rows = records.selectOrder(consultationId);
        return rows.isEmpty() ? null : mapOrder(rows.get(0));
    }

    public String consultationIdOfOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return null;
        }
        String compact = orderId.replace("-", "");
        List<String> rows = records.consultationIdOfOrder(orderId, compact);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 插入一笔新订单。同一咨询只能有一行未删除订单。 */
    public void insertOrder(String consultationId, ConsultOrder order) {
        records.insertOrder(new OrderInsert(
                order.orderId(),
                consultationId,
                order.packageCode(),
                order.packageName(),
                order.originAmount(),
                order.payAmount(),
                order.discountAmount(),
                order.benefitAmount() == null ? BigDecimal.ZERO : order.benefitAmount(),
                order.benefitCardId(),
                order.benefitReady() ? 1 : 0,
                order.payStatus().getCode(),
                order.paidAt(),
                order.doctor() == null ? null : order.doctor().id(),
                order.doctor() == null ? null : order.doctor().name(),
                order.doctor() == null ? null : order.doctor().title(),
                order.doctor() == null ? null : order.doctor().hospital(),
                order.doctor() == null ? null : order.doctor().departmentCode(),
                order.doctor() == null ? null : order.doctor().specialty(),
                order.doctor() == null ? null : blankToNull(order.doctor().evidence()),
                order.doctor() == null ? null : order.doctor().scheduleId(),
                order.consultMinutes(),
                order.endsAt(),
                order.endedAt(),
                order.endReason(),
                Instant.now()));
    }

    private ConsultOrder mapOrder(OrderRow row) {
        String doctorId = row.doctorId();
        AssignedDoctorRes doctor = doctorId == null || doctorId.isBlank()
                ? null
                : new AssignedDoctorRes(
                        doctorId,
                        row.doctorName(),
                        row.doctorTitle(),
                        row.hospital(),
                        nz(row.departmentCode()),
                        row.specialty(),
                        null,
                        row.skillsHit(),
                        row.scheduleId());
        int minutes = row.consultMinutes() == null || row.consultMinutes() <= 0 ? 15 : row.consultMinutes();
        BigDecimal benefitAmount = row.benefitAmount();
        return new ConsultOrder(
                row.id(),
                nz(row.packageCode()),
                row.packageName(),
                row.originAmount(),
                row.payAmount(),
                row.discountAmount(),
                PayStatus.fromCode(nz(row.payStatus())),
                row.paidAt(),
                doctor,
                minutes,
                row.endsAt(),
                row.endedAt(),
                row.endReason(),
                benefitAmount == null ? BigDecimal.ZERO : benefitAmount,
                row.benefitCardId(),
                nz(row.benefitReady()) == 1);
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

    /** 已支付还没派上医生的单，按支付时间先进先出。 */
    public List<MatchQueue.Entry> listUnmatchedPaid(int limit) {
        int size = Math.max(1, Math.min(limit, 5000));
        List<QueueRow> rows = records.listUnmatchedPaid(size);
        List<MatchQueue.Entry> out = new ArrayList<>(rows.size());
        for (QueueRow row : rows) {
            out.add(new MatchQueue.Entry(
                    row.consultationId(),
                    row.orderId(),
                    row.paidAt(),
                    nz(row.packageCode()),
                    row.specialty()));
        }
        return out;
    }

    /** 还没对齐的权益单：已支付要确认，取消的待支付要退回。成功的不再出现。 */
    public List<BenefitLedgerRow> listBenefitLedger(int limit) {
        int size = Math.max(1, Math.min(limit, 200));
        List<BenefitRow> rows = records.listBenefitLedger(size);
        List<BenefitLedgerRow> out = new ArrayList<>(rows.size());
        for (BenefitRow row : rows) {
            out.add(new BenefitLedgerRow(
                    row.id(),
                    nz(row.payStatus()) == 2,
                    "CANCEL".equals(row.endReason())));
        }
        return out;
    }

    /** 确认或退回已经成功，对账不再选这笔。 */
    public void markBenefitSettled(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return;
        }
        records.markBenefitSettled(orderId);
    }

    /** 未确认卡片挂着该二级科的会话数。近似。card_specialty 是 card_json 的生成列，走 idx_consult_session_card。 */
    public int countCardReady(String specialty) {
        Integer n = records.countCardReady(specialty);
        return n == null ? 0 : n;
    }

    /** 已确认未下单、卡片仍是该二级科。 */
    public int countConfirmedUnpaid(String specialty) {
        Integer n = records.countConfirmedUnpaid(specialty);
        return n == null ? 0 : n;
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

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }
}
