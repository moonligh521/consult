package com.moonligh.consult.agent.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.ServiceType;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.FallbackReason;
import com.moonligh.consult.agent.domain.safety.MessageSource;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;

/**
 * 一轮咨询的聚合。业务状态与安全锁存位分存；原话只追加。
 */
public class ConsultationSession {

    /** 本轮咨询编号。 */
    private final String id;
    /** 业务状态：待描述、待补槽、卡片待确认、已确认。 */
    private ConsultationStatus businessStatus = ConsultationStatus.NEED_DESCRIPTION;
    /** 用户要办的服务：线下挂号、互联网复诊、远程会诊。 */
    private ServiceType serviceType;
    /** 已抽出的性别。空着表示还没抽到。 */
    private Gender gender;
    /** 已抽出的年龄。空着表示还没抽到。 */
    private Integer age;
    /** 已抽出的病程，例如「半年」。 */
    private String duration;
    /** 卡片展示描述，可编辑；不是原话记录。 */
    private String description = "";
    /** 已经生成的咨询卡片。还没齐或已作废时为 null。 */
    private ConsultationCardRes card;
    /** 急症锁存：一旦为 true，本轮不再降级。 */
    private boolean urgentLatched;
    /** 需尽快评估。可以带提示，但不停止挂号。 */
    private boolean promptEvaluationLatched;
    /** 患者明确要求转人工。 */
    private boolean humanRequestedLatched;
    /** 当前线上渠道不适用。 */
    private boolean unsuitableLatched;
    /** 规则或模型说不清楚，走安全降级。 */
    private boolean undetermined;
    /** 降级原因。没降级时为 null。 */
    private FallbackReason fallbackReason;
    /** 急症提示用哪条模板。 */
    private String urgentTemplateId;
    /** 尽快评估提示用哪条模板。 */
    private String noticeTemplateId;
    /** 最后一条原话的序号。 */
    private int lastMessageSeq;
    /** 已经评估到哪一条原话。小于 lastMessageSeq 就要补评。 */
    private int lastEvaluatedSeq;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    /** 本轮全部原话，只追加不覆盖。 */
    private final List<ConsultationMessage> messages = new ArrayList<>();
    /** 本轮评估产生、还没写入数据库的审计。 */
    private final List<SafetyDecisionAudit> pendingAudits = new ArrayList<>();
    /** 已经落库的原话条数。后面只插入新增的。 */
    private int persistedMessageCount;

    /** 新建一轮咨询。编号由仓储生成或调用方传入。 */
    public ConsultationSession(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public ConsultationStatus getBusinessStatus() {
        return businessStatus;
    }

    public void setBusinessStatus(ConsultationStatus businessStatus) {
        this.businessStatus = businessStatus;
        this.updatedAt = Instant.now();
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public ConsultationCardRes getCard() {
        return card;
    }

    public void setCard(ConsultationCardRes card) {
        this.card = card;
    }

    public boolean isUrgentLatched() {
        return urgentLatched;
    }

    public void setUrgentLatched(boolean urgentLatched) {
        this.urgentLatched = urgentLatched;
    }

    public boolean isPromptEvaluationLatched() {
        return promptEvaluationLatched;
    }

    public void setPromptEvaluationLatched(boolean promptEvaluationLatched) {
        this.promptEvaluationLatched = promptEvaluationLatched;
    }

    public boolean isHumanRequestedLatched() {
        return humanRequestedLatched;
    }

    public void setHumanRequestedLatched(boolean humanRequestedLatched) {
        this.humanRequestedLatched = humanRequestedLatched;
    }

    public boolean isUnsuitableLatched() {
        return unsuitableLatched;
    }

    public void setUnsuitableLatched(boolean unsuitableLatched) {
        this.unsuitableLatched = unsuitableLatched;
    }

    public boolean isUndetermined() {
        return undetermined;
    }

    public void setUndetermined(boolean undetermined) {
        this.undetermined = undetermined;
    }

    public FallbackReason getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(FallbackReason fallbackReason) {
        this.fallbackReason = fallbackReason;
    }

    public String getUrgentTemplateId() {
        return urgentTemplateId;
    }

    public void setUrgentTemplateId(String urgentTemplateId) {
        this.urgentTemplateId = urgentTemplateId;
    }

    public String getNoticeTemplateId() {
        return noticeTemplateId;
    }

    public void setNoticeTemplateId(String noticeTemplateId) {
        this.noticeTemplateId = noticeTemplateId;
    }

    public int getLastMessageSeq() {
        return lastMessageSeq;
    }

    public void setLastMessageSeq(int lastMessageSeq) {
        this.lastMessageSeq = lastMessageSeq;
    }

    public int getLastEvaluatedSeq() {
        return lastEvaluatedSeq;
    }

    public void setLastEvaluatedSeq(int lastEvaluatedSeq) {
        this.lastEvaluatedSeq = lastEvaluatedSeq;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** 返回原话副本，外面改不了内部列表。 */
    public List<ConsultationMessage> messages() {
        return List.copyOf(messages);
    }

    /** 从数据库装回原话。已经保存过的条数记下来，后面只插入新增的。 */
    public void restoreMessages(List<ConsultationMessage> loaded) {
        messages.clear();
        messages.addAll(loaded);
        persistedMessageCount = loaded.size();
        lastMessageSeq = loaded.isEmpty() ? 0 : loaded.get(loaded.size() - 1).seq();
    }

    /** 这个 messageId 是不是已经入库过。用来做幂等。 */
    public boolean hasMessage(String messageId) {
        if (messageId == null) {
            return false;
        }
        for (ConsultationMessage message : messages) {
            if (messageId.equals(message.messageId())) {
                return true;
            }
        }
        return false;
    }

    /** 追加一条原话。不覆盖以前说过的话。 */
    public ConsultationMessage appendMessage(String messageId, MessageSource source, String text) {
        // 没带 messageId 就现场生成一个，方便幂等和审计对照。
        String id = messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId;
        lastMessageSeq = lastMessageSeq + 1;
        ConsultationMessage message = new ConsultationMessage(lastMessageSeq, id, source, text, Instant.now());
        messages.add(message);
        updatedAt = Instant.now();
        return message;
    }

    /** 本轮还没写入数据库的原话。 */
    public List<ConsultationMessage> newMessages() {
        return List.copyOf(messages.subList(persistedMessageCount, messages.size()));
    }

    /** 保存成功后，把新增原话标记成已落库。 */
    public void markMessagesPersisted() {
        persistedMessageCount = messages.size();
    }

    /** 本轮评估产生的审计，等保存时一起写入。 */
    public void addPendingAudit(SafetyDecisionAudit audit) {
        pendingAudits.add(audit);
    }

    /** 取出待写审计并清空列表。 */
    public List<SafetyDecisionAudit> drainPendingAudits() {
        List<SafetyDecisionAudit> drained = List.copyOf(pendingAudits);
        pendingAudits.clear();
        return drained;
    }
}
