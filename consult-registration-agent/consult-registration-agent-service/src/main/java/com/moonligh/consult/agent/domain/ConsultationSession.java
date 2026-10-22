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

    private final String id;
    private ConsultationStatus businessStatus = ConsultationStatus.NEED_DESCRIPTION;
    private ServiceType serviceType;
    private Gender gender;
    private Integer age;
    private String duration;
    private String description = "";
    private ConsultationCardRes card;
    private boolean urgentLatched;
    private boolean promptEvaluationLatched;
    private boolean humanRequestedLatched;
    private boolean unsuitableLatched;
    private boolean undetermined;
    private FallbackReason fallbackReason;
    private String urgentTemplateId;
    private String noticeTemplateId;
    private int lastMessageSeq;
    private int lastEvaluatedSeq;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private final List<ConsultationMessage> messages = new ArrayList<>();
    private final List<SafetyDecisionAudit> pendingAudits = new ArrayList<>();
    private int persistedMessageCount;

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

    public List<ConsultationMessage> messages() {
        return List.copyOf(messages);
    }

    public void restoreMessages(List<ConsultationMessage> loaded) {
        messages.clear();
        messages.addAll(loaded);
        persistedMessageCount = loaded.size();
        lastMessageSeq = loaded.isEmpty() ? 0 : loaded.get(loaded.size() - 1).seq();
    }

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

    public ConsultationMessage appendMessage(String messageId, MessageSource source, String text) {
        String id = messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId;
        lastMessageSeq = lastMessageSeq + 1;
        ConsultationMessage message = new ConsultationMessage(lastMessageSeq, id, source, text, Instant.now());
        messages.add(message);
        updatedAt = Instant.now();
        return message;
    }

    public List<ConsultationMessage> newMessages() {
        return List.copyOf(messages.subList(persistedMessageCount, messages.size()));
    }

    public void markMessagesPersisted() {
        persistedMessageCount = messages.size();
    }

    public void addPendingAudit(SafetyDecisionAudit audit) {
        pendingAudits.add(audit);
    }

    public List<SafetyDecisionAudit> drainPendingAudits() {
        List<SafetyDecisionAudit> drained = List.copyOf(pendingAudits);
        pendingAudits.clear();
        return drained;
    }
}
