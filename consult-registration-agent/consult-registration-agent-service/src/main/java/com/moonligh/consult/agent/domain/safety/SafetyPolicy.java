package com.moonligh.consult.agent.domain.safety;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Handoff;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.enums.ServiceType;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.Slots;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 根据安全锁存位组装对外响应。ConsultationRes 只从这里生成。
 */
public final class SafetyPolicy {

    public static final String REDESCRIBE_PROMPT = "请重新描述病情。";

    private SafetyPolicy() {
    }

    public static void merge(ConsultationSession session, SafetyAssessment assessment) {
        if (assessment.urgent()) {
            session.setUrgentLatched(true);
            if (assessment.templateId() != null) {
                session.setUrgentTemplateId(assessment.templateId());
            }
        }
        if (assessment.promptEvaluation()) {
            session.setPromptEvaluationLatched(true);
            if (assessment.templateId() != null) {
                session.setNoticeTemplateId(assessment.templateId());
            }
        }
        if (assessment.humanRequested()) {
            session.setHumanRequestedLatched(true);
        }
        if (assessment.unsuitableForOnline()) {
            session.setUnsuitableLatched(true);
        }
        session.setUndetermined(assessment.undetermined());
        session.setFallbackReason(assessment.fallbackReason());
    }

    public static EffectiveSafety effective(ConsultationSession session) {
        Urgency urgency;
        if (session.isUrgentLatched()) {
            urgency = Urgency.URGENT;
        } else if (session.isUndetermined()) {
            urgency = Urgency.UNDETERMINED;
        } else if (session.isPromptEvaluationLatched()) {
            urgency = Urgency.PROMPT_EVALUATION;
        } else {
            urgency = Urgency.NONE;
        }
        ChannelDecision channel;
        ServiceType serviceType = session.getServiceType();
        if (session.isHumanRequestedLatched()) {
            channel = ChannelDecision.NEED_HUMAN;
        } else if (session.isUnsuitableLatched() && serviceType != ServiceType.OFFLINE_APPOINTMENT) {
            channel = ChannelDecision.UNSUITABLE_FOR_ONLINE;
        } else if (serviceType == ServiceType.ONLINE_FOLLOW_UP || serviceType == ServiceType.REMOTE_CONSULT) {
            channel = ChannelDecision.PRECHECK_REQUIRED;
        } else {
            channel = ChannelDecision.ALLOWED;
        }
        return new EffectiveSafety(urgency, channel);
    }

    public static boolean isSafetyStop(ConsultationSession session) {
        EffectiveSafety effective = effective(session);
        return effective.urgency() == Urgency.URGENT
                || effective.channel() == ChannelDecision.NEED_HUMAN
                || effective.urgency() == Urgency.UNDETERMINED
                || effective.channel() == ChannelDecision.UNSUITABLE_FOR_ONLINE;
    }

    public static ConsultationRes view(ConsultationSession session, SafetyCatalog catalog) {
        EffectiveSafety effective = effective(session);
        if (effective.urgency() == Urgency.URGENT) {
            String prompt = catalog.templateText(orDefault(session.getUrgentTemplateId(), SafetyCatalog.URGENT_DEFAULT));
            return safetyView(session, ConsultationStatus.URGENT, prompt);
        }
        if (effective.channel() == ChannelDecision.NEED_HUMAN) {
            return safetyView(session, ConsultationStatus.NEED_HUMAN, catalog.templateText(SafetyCatalog.HUMAN_DEFAULT));
        }
        if (effective.urgency() == Urgency.UNDETERMINED) {
            return safetyView(session, ConsultationStatus.SAFE_FALLBACK, catalog.templateText(SafetyCatalog.FALLBACK_DEFAULT));
        }
        if (effective.channel() == ChannelDecision.UNSUITABLE_FOR_ONLINE) {
            return safetyView(session, ConsultationStatus.UNSUITABLE_FOR_ONLINE, catalog.templateText(SafetyCatalog.UNSUITABLE_DEFAULT));
        }
        String notice = effective.urgency() == Urgency.PROMPT_EVALUATION
                ? catalog.templateText(orDefault(session.getNoticeTemplateId(), SafetyCatalog.URGENT_DEFAULT))
                : null;
        ConsultationStatus business = session.getBusinessStatus();
        if (business == ConsultationStatus.NEED_DESCRIPTION) {
            return new ConsultationRes(session.getId(), business, REDESCRIBE_PROMPT, null, null, notice, null);
        }
        if (business == ConsultationStatus.NEED_SLOT) {
            MissingSlot missing = Slots.nextMissing(session);
            return new ConsultationRes(session.getId(), business, Slots.promptFor(missing), missing, null, notice, null);
        }
        Handoff handoff = handoffOf(session.getServiceType());
        return new ConsultationRes(session.getId(), business, null, null, session.getCard(), notice, handoff);
    }

    public static void applyStopCardPolicy(ConsultationSession session) {
        if (session.getBusinessStatus() == ConsultationStatus.CARD_READY) {
            session.setCard(null);
            session.setBusinessStatus(ConsultationStatus.NEED_SLOT);
        }
    }

    public static ErrorCode stopError(ConsultationSession session) {
        EffectiveSafety effective = effective(session);
        if (effective.urgency() == Urgency.URGENT) {
            return ErrorCode.URGENT_STOP;
        }
        if (effective.channel() == ChannelDecision.NEED_HUMAN) {
            return ErrorCode.HUMAN_STOP;
        }
        if (effective.urgency() == Urgency.UNDETERMINED) {
            return ErrorCode.SAFE_FALLBACK_STOP;
        }
        return ErrorCode.UNSUITABLE_STOP;
    }

    public static void rejectIfSafetyStop(ConsultationSession session) {
        if (isSafetyStop(session)) {
            throw new BusinessException(stopError(session));
        }
    }

    public static Handoff handoffOf(ServiceType serviceType) {
        if (serviceType == ServiceType.ONLINE_FOLLOW_UP || serviceType == ServiceType.REMOTE_CONSULT) {
            return Handoff.PRECHECK;
        }
        return Handoff.PRE_APPOINTMENT_INFO;
    }

    private static ConsultationRes safetyView(ConsultationSession session, ConsultationStatus status, String prompt) {
        return new ConsultationRes(session.getId(), status, prompt, null, null, null, null);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
