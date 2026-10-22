package com.moonligh.consult.agent.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.ServiceType;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.ConsultationRepository;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.Slots;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.agent.domain.safety.AuditTrigger;
import com.moonligh.consult.agent.domain.safety.Demographics;
import com.moonligh.consult.agent.domain.safety.EvaluationKind;
import com.moonligh.consult.agent.domain.safety.MessageSource;
import com.moonligh.consult.agent.domain.safety.ModelStatus;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SafetyAssessment;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.safety.SafetyPolicy;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 咨询编排。原话先入库并完成安全评估，再推进业务状态。
 */
@Service
public class ConsultationService {

    private final ConsultationRepository repository;
    private final SafetyGate safetyGate;
    private final SlotParser slotParser;
    private final SymptomExtractor symptomExtractor;
    private final DepartmentRouter departmentRouter;
    private final SafetyMetrics metrics;

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            DepartmentRouter departmentRouter,
            SafetyMetrics metrics) {
        this.repository = repository;
        this.safetyGate = safetyGate;
        this.slotParser = slotParser;
        this.symptomExtractor = symptomExtractor;
        this.departmentRouter = departmentRouter;
        this.metrics = metrics;
    }

    public ConsultationRes acceptMessage(String consultationId, String text) {
        return acceptMessage(consultationId, null, null, text);
    }

    /**
     * 接收一条病情描述。
     */
    public ConsultationRes acceptMessage(String consultationId, String messageId, Integer serviceTypeCode, String text) {
        if (messageId != null && messageId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        ServiceType requestedType = parseServiceType(serviceTypeCode);
        return repository.runLocked(consultationId, session -> acceptLocked(session, messageId, requestedType, text));
    }

    /**
     * 修改已生成的咨询卡片。安全停止态不可修改。
     */
    public ConsultationRes updateCard(CardPatchReq patch) {
        if (patch == null) {
            throw new BusinessException(ErrorCode.EMPTY_CARD_PATCH);
        }
        return repository.runLocked(patch.consultationId(), session -> updateLocked(session, patch));
    }

    /**
     * 确认咨询卡片。确认后若再提交病情，仍先做安全评估。
     */
    public ConsultationRes confirm(String consultationId) {
        return repository.runLocked(consultationId, this::confirmLocked);
    }

    private ConsultationRes acceptLocked(
            ConsultationSession session,
            String messageId,
            ServiceType requestedType,
            String text) {
        if (messageId != null && session.hasMessage(messageId)) {
            return SafetyPolicy.view(session, catalog());
        }
        String sentence = text == null ? "" : text.trim();
        if (sentence.isEmpty()) {
            if (SafetyPolicy.isSafetyStop(session)) {
                return SafetyPolicy.view(session, catalog());
            }
            return blankView(session);
        }
        session.appendMessage(messageId, MessageSource.MESSAGE, sentence);
        assess(session, AuditTrigger.MESSAGE);
        boolean serviceTypeRejected = applyServiceType(session, requestedType);
        // 安全停止优先于业务错误，避免急症被卡片待确认等状态盖住。
        if (SafetyPolicy.isSafetyStop(session)) {
            SafetyPolicy.applyStopCardPolicy(session);
            return buildResponse(session);
        }
        if (serviceTypeRejected) {
            throw new BusinessException(ErrorCode.SERVICE_TYPE_LOCKED);
        }
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getBusinessStatus() == ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_PENDING_CONFIRMATION);
        }
        fillEmptySlots(session, sentence);
        addDescription(session, sentence);
        return nextView(session);
    }

    private ConsultationRes updateLocked(ConsultationSession session, CardPatchReq patch) {
        SafetyPolicy.rejectIfSafetyStop(session);
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getBusinessStatus() != ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_NOT_READY);
        }
        if (!hasCardChange(patch)) {
            throw new BusinessException(ErrorCode.EMPTY_CARD_PATCH);
        }
        ConsultationCardRes current = session.getCard();
        Gender gender = current.gender();
        Integer age = current.age();
        String duration = current.duration();
        String description = current.description();
        Department department = current.department();
        List<String> symptoms = current.symptoms();
        boolean descriptionChanged = false;
        if (patch.gender() != null) {
            gender = patch.gender();
        }
        if (patch.age() != null) {
            if (patch.age() < 1 || patch.age() > 120) {
                throw new BusinessException(ErrorCode.INVALID_AGE);
            }
            age = patch.age();
        }
        if (patch.duration() != null) {
            String value = patch.duration().trim();
            if (!slotParser.validDuration(value)) {
                throw new BusinessException(ErrorCode.INVALID_DURATION);
            }
            duration = value;
        }
        if (patch.description() != null) {
            description = patch.description().trim();
            descriptionChanged = true;
        }
        if (patch.department() != null) {
            department = patch.department();
        }
        session.setGender(gender);
        session.setAge(age);
        session.setDuration(duration);
        session.setDescription(description);
        if (descriptionChanged) {
            // 改卡描述追加进原话，安全评估扫描全部原话。
            session.appendMessage(null, MessageSource.CARD_EDIT, description);
            assess(session, AuditTrigger.CARD_EDIT);
            if (SafetyPolicy.isSafetyStop(session)) {
                SafetyPolicy.applyStopCardPolicy(session);
                return buildResponse(session);
            }
            symptoms = symptomExtractor.extract(description);
            if (patch.department() == null) {
                department = departmentRouter.recommend(description);
            }
        }
        ConsultationCardRes card = new ConsultationCardRes(gender, age, duration, description, symptoms, department);
        session.setCard(card);
        session.setBusinessStatus(ConsultationStatus.CARD_READY);
        return buildResponse(session);
    }

    private ConsultationRes confirmLocked(ConsultationSession session) {
        if (session.getLastEvaluatedSeq() < session.getLastMessageSeq()) {
            assess(session, AuditTrigger.CONFIRM_RECHECK);
        }
        SafetyPolicy.rejectIfSafetyStop(session);
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getBusinessStatus() != ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_NOT_READY);
        }
        session.setBusinessStatus(ConsultationStatus.CONFIRMED);
        return buildResponse(session);
    }

    private void assess(ConsultationSession session, AuditTrigger trigger) {
        int previousStatus = SafetyPolicy.view(session, catalog()).status().getCode();
        int businessBefore = session.getBusinessStatus().getCode();
        SafetyAssessment assessment;
        EvaluationKind kind;
        if (session.isUrgentLatched()) {
            // 急症锁存后不再降级。
            assessment = SafetyAssessment.none(ModelStatus.SKIPPED);
            kind = EvaluationKind.PINNED_URGENT;
        } else {
            assessment = safetyGate.assess(session.messages(), new Demographics(session.getAge(), session.getGender()));
            SafetyPolicy.merge(session, assessment);
            session.setLastEvaluatedSeq(session.getLastMessageSeq());
            kind = EvaluationKind.EVALUATED;
        }
        ConsultationRes view = SafetyPolicy.view(session, catalog());
        session.addPendingAudit(new SafetyDecisionAudit(
                UUID.randomUUID().toString(),
                session.getId(),
                session.getLastMessageSeq(),
                trigger,
                kind,
                assessment.matchedRuleIds(),
                assessment.shadowRuleIds(),
                assessment.evidence(),
                catalog().catalogVersion(),
                RuleSafetyEngine.VERSION,
                assessment.modelStatus(),
                null,
                assessment.fallbackReason(),
                previousStatus,
                view.status().getCode(),
                businessBefore,
                session.getBusinessStatus().getCode(),
                view.prompt() == null ? null : templateIdOf(view.status()),
                Instant.now()));
        metrics.increment("status." + view.status().getCode());
        if (!assessment.shadowRuleIds().isEmpty()) {
            metrics.increment("shadow.hit");
        }
    }

    private String templateIdOf(ConsultationStatus status) {
        if (status == ConsultationStatus.URGENT) {
            return SafetyCatalog.URGENT_DEFAULT;
        }
        if (status == ConsultationStatus.NEED_HUMAN) {
            return SafetyCatalog.HUMAN_DEFAULT;
        }
        if (status == ConsultationStatus.SAFE_FALLBACK) {
            return SafetyCatalog.FALLBACK_DEFAULT;
        }
        if (status == ConsultationStatus.UNSUITABLE_FOR_ONLINE) {
            return SafetyCatalog.UNSUITABLE_DEFAULT;
        }
        return null;
    }

    /** 确认后修改服务类型返回 true。 */
    private boolean applyServiceType(ConsultationSession session, ServiceType requestedType) {
        if (requestedType == null) {
            return false;
        }
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED
                && session.getServiceType() != null
                && requestedType != session.getServiceType()) {
            return true;
        }
        session.setServiceType(requestedType);
        return false;
    }

    private ServiceType parseServiceType(Integer code) {
        if (code == null) {
            return null;
        }
        try {
            return ServiceType.fromCode(code);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.INVALID_SERVICE_TYPE);
        }
    }

    /** 已填写的性别、年龄、病程不覆盖。 */
    private void fillEmptySlots(ConsultationSession session, String sentence) {
        if (session.getGender() == null) {
            Gender gender = slotParser.gender(sentence);
            if (gender != null) {
                session.setGender(gender);
            }
        }
        if (session.getAge() == null) {
            Integer age = slotParser.age(sentence);
            if (age != null) {
                session.setAge(age);
            }
        }
        if (session.getDuration() == null) {
            String duration = slotParser.duration(sentence);
            if (duration != null) {
                session.setDuration(duration);
            }
        }
    }

    /** 卡片展示描述，与原话记录分开。 */
    private void addDescription(ConsultationSession session, String sentence) {
        if (session.getDescription().isEmpty()) {
            session.setDescription(sentence);
            return;
        }
        session.setDescription(session.getDescription() + "\n" + sentence);
    }

    private ConsultationRes nextView(ConsultationSession session) {
        if (Slots.nextMissing(session) != null) {
            session.setBusinessStatus(ConsultationStatus.NEED_SLOT);
            session.setCard(null);
            return buildResponse(session);
        }
        ConsultationCardRes card = new ConsultationCardRes(
                session.getGender(),
                session.getAge(),
                session.getDuration(),
                session.getDescription(),
                symptomExtractor.extract(session.getDescription()),
                departmentRouter.recommend(session.getDescription()));
        session.setCard(card);
        session.setBusinessStatus(ConsultationStatus.CARD_READY);
        return buildResponse(session);
    }

    private ConsultationRes blankView(ConsultationSession session) {
        if (session.getBusinessStatus() != ConsultationStatus.NEED_SLOT) {
            session.setBusinessStatus(ConsultationStatus.NEED_DESCRIPTION);
        }
        return buildResponse(session);
    }

    private ConsultationRes buildResponse(ConsultationSession session) {
        ConsultationRes view = SafetyPolicy.view(session, catalog());
        metrics.increment("view." + view.status().getCode());
        return view;
    }

    private boolean hasCardChange(CardPatchReq patch) {
        return patch.gender() != null
                || patch.age() != null
                || patch.duration() != null
                || patch.description() != null
                || patch.department() != null;
    }

    private SafetyCatalog catalog() {
        return safetyGate.catalog();
    }
}
