package com.moonligh.consult.agent.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.moonligh.consult.agent.api.res.AdminConsultationRes;
import com.moonligh.consult.agent.api.res.ConsultationRecordRes;

@Mapper
public interface ConsultationRecordMapper {

    int insertBlank(@Param("id") String id, @Param("createdAt") Instant createdAt);

    List<AdminConsultationRes> listRecent(
            @Param("fromAt") Instant fromAt, @Param("toAt") Instant toAt, @Param("limit") int limit);

    Integer countSession(@Param("id") String id);

    ConsultationRecords.LatestMatch latestMatch(@Param("consultationId") String consultationId);

    SessionRow selectByIdForUpdate(@Param("id") String id);

    SessionRow selectById(@Param("id") String id);

    List<ConsultationRecordRes> listMine(
            @Param("accountId") String accountId,
            @Param("fromAt") Instant fromAt,
            @Param("limit") int limit,
            @Param("offset") int offset);

    Long countMine(@Param("accountId") String accountId, @Param("fromAt") Instant fromAt);

    List<String> findRecentOpening(
            @Param("accountId") String accountId, @Param("text") String text, @Param("since") Instant since);

    List<MessageRow> selectMessages(@Param("id") String id);

    int updateSession(SessionUpdate row);

    int updateSessionTranscript(SessionUpdate row);

    int insertMessage(
            @Param("consultationId") String consultationId,
            @Param("seq") int seq,
            @Param("messageId") String messageId,
            @Param("source") int source,
            @Param("text") String text,
            @Param("receivedAt") Instant receivedAt);

    int insertAudit(AuditInsert row);

    int insertDecision(DecisionInsert row);

    List<FunnelRow> funnel(@Param("fromAt") Instant fromAt);

    List<OrderRow> selectOrder(@Param("consultationId") String consultationId);

    List<String> consultationIdOfOrder(@Param("orderId") String orderId, @Param("compact") String compact);

    int insertOrder(OrderInsert row);

    List<QueueRow> listUnmatchedPaid(@Param("limit") int limit);

    List<BenefitRow> listBenefitLedger(@Param("limit") int limit);

    int markBenefitSettled(@Param("orderId") String orderId);

    Integer countCardReady(@Param("specialty") String specialty);

    Integer countConfirmedUnpaid(@Param("specialty") String specialty);

    class SessionRow {
    public String id;
    public Integer businessStatus;
    public Integer gender;
    public Integer age;
    public String patientId;
    public String patientName;
    public String accountId;
    public String title;
    public String duration;
    public String description;
    public String cardJson;
    public String triageJson;
    public Boolean urgentLatched;
    public Boolean promptEvaluationLatched;
    public Boolean humanRequestedLatched;
    public Boolean unsuitableLatched;
    public Boolean undetermined;
    public String fallbackReason;
    public String urgentTemplateId;
    public String noticeTemplateId;
    public Integer lastMessageSeq;
    public Integer lastEvaluatedSeq;
    public Instant createdAt;
    public Instant updatedAt;
    public String transcriptJson;
}

record MessageRow(Integer seq, String messageId, Integer source, String text, Instant receivedAt) {
}

record OrderRow(
        String id,
        Integer packageCode,
        String packageName,
        BigDecimal originAmount,
        BigDecimal payAmount,
        BigDecimal discountAmount,
        BigDecimal benefitAmount,
        String benefitCardId,
        Integer benefitReady,
        Integer payStatus,
        Instant paidAt,
        String doctorId,
        String doctorName,
        String doctorTitle,
        String hospital,
        Integer departmentCode,
        String specialty,
        String skillsHit,
        String scheduleId,
        Integer consultMinutes,
        Instant endsAt,
        Instant endedAt,
        String endReason) {
}

record FunnelRow(int node, int source, String reasonCode, long total, long modelCalls, double avgLatency) {
}

record QueueRow(String consultationId, String orderId, Instant paidAt, Integer packageCode, String specialty) {
}

record BenefitRow(String id, Integer payStatus, String endReason) {
}

record SessionUpdate(
        int businessStatus,
        Integer gender,
        Integer age,
        String patientId,
        String patientName,
        String accountId,
        String title,
        String duration,
        String description,
        String cardJson,
        String triageJson,
        boolean urgentLatched,
        boolean promptEvaluationLatched,
        boolean humanRequestedLatched,
        boolean unsuitableLatched,
        boolean undetermined,
        String fallbackReason,
        String urgentTemplateId,
        String noticeTemplateId,
        int lastMessageSeq,
        int lastEvaluatedSeq,
        Instant updatedAt,
        String transcriptJson,
        String id) {
}

record AuditInsert(
        String decisionId,
        String consultationId,
        int messageSeq,
        int triggerType,
        int evaluation,
        int certainty,
        String matchedRuleIds,
        String shadowRuleIds,
        String modelTriggerIds,
        String evidenceRefs,
        String catalogVersion,
        String ruleEngineVersion,
        int modelStatus,
        String modelVersion,
        String fallbackReason,
        int previousStatus,
        int resultingStatus,
        int businessBefore,
        int businessAfter,
        String templateId,
        Instant decisionTime) {
}

record DecisionInsert(
        String decisionId,
        String runId,
        String consultationId,
        String orderId,
        int messageSeq,
        int triggerType,
        int node,
        int certainty,
        int source,
        String reasonCode,
        String outputJson,
        String evidenceRefs,
        String shadowIds,
        Integer modelStatus,
        Boolean modelCached,
        Integer modelLatencyMs,
        int latencyMs,
        String catalogVersion,
        String promptVersion,
        String modelName,
        String inputFingerprint,
        Instant decisionTime) {
}

record OrderInsert(
        String orderId,
        String consultationId,
        int packageCode,
        String packageName,
        BigDecimal originAmount,
        BigDecimal payAmount,
        BigDecimal discountAmount,
        BigDecimal benefitAmount,
        String benefitCardId,
        int benefitReady,
        int payStatus,
        Instant paidAt,
        String doctorId,
        String doctorName,
        String doctorTitle,
        String hospital,
        Integer departmentCode,
        String specialty,
        String skillsHit,
        String scheduleId,
        int consultMinutes,
        Instant endsAt,
        Instant endedAt,
        String endReason,
        Instant createdAt) {
    }
}
