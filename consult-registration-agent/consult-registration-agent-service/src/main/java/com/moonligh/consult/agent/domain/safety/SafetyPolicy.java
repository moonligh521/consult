package com.moonligh.consult.agent.domain.safety;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Handoff;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.enums.ServiceType;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.ComplaintClarity;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.Slots;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 根据会话里的安全锁存位，算出给前端看的状态。
 * 对外 ConsultationRes 只从这里生成。
 */
public final class SafetyPolicy {

    /** 空描述时请对方重新说一遍。 */
    public static final String REDESCRIBE_PROMPT = "请重新描述病情。";
    /** 说了话但不够具体，先补病情再选就诊人。 */
    public static final String DETAIL_PROMPT = ComplaintClarity.DETAIL_PROMPT;

    private SafetyPolicy() {
    }

    /** 把本轮评估结果合并进锁存位。只升不降。 */
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

    /** 当前有效的紧急程度和渠道。急症优先于转人工。 */
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

    /** 急症、转人工、安全降级、渠道不适用，都算安全停止。 */
    public static boolean isSafetyStop(ConsultationSession session) {
        EffectiveSafety effective = effective(session);
        return effective.urgency() == Urgency.URGENT
                || effective.channel() == ChannelDecision.NEED_HUMAN
                || effective.urgency() == Urgency.UNDETERMINED
                || effective.channel() == ChannelDecision.UNSUITABLE_FOR_ONLINE;
    }

    /** 拼出给前端的画面。安全停止时不带卡片。 */
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
            boolean hasSpoken = false;
            for (ConsultationMessage message : session.messages()) {
                if (message.text() != null && !message.text().isBlank()) {
                    hasSpoken = true;
                    break;
                }
            }
            String prompt = hasSpoken ? DETAIL_PROMPT : REDESCRIBE_PROMPT;
            return new ConsultationRes(session.getId(), business, prompt, null, null, notice, null);
        }
        if (business == ConsultationStatus.NEED_SLOT) {
            MissingSlot missing = Slots.nextMissing(session);
            return new ConsultationRes(session.getId(), business, Slots.promptFor(missing), missing, null, notice, null);
        }
        Handoff handoff = handoffOf(session.getServiceType());
        return new ConsultationRes(session.getId(), business, null, null, session.getCard(), notice, handoff);
    }

    /** 安全停止时：如果卡片还在待确认，就把卡片作废。 */
    public static void applyStopCardPolicy(ConsultationSession session) {
        if (session.getBusinessStatus() == ConsultationStatus.CARD_READY) {
            session.setCard(null);
            session.setBusinessStatus(ConsultationStatus.NEED_SLOT);
        }
    }

    /** 安全停止时对应的错误码：1103、1110、1111、1112。 */
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

    /** 安全停止态不允许改卡或确认，直接抛停止码。 */
    public static void rejectIfSafetyStop(ConsultationSession session) {
        if (isSafetyStop(session)) {
            throw new BusinessException(stopError(session));
        }
    }

    /** 互联网复诊、远程会诊交给预审；其余是预约前信息整理。 */
    public static Handoff handoffOf(ServiceType serviceType) {
        if (serviceType == ServiceType.ONLINE_FOLLOW_UP || serviceType == ServiceType.REMOTE_CONSULT) {
            return Handoff.PRECHECK;
        }
        return Handoff.PRE_APPOINTMENT_INFO;
    }

    /** 安全停止画面：只有提示，不带卡片。 */
    private static ConsultationRes safetyView(ConsultationSession session, ConsultationStatus status, String prompt) {
        return new ConsultationRes(session.getId(), status, prompt, null, null, null, null);
    }

    /** 空模板编号时用默认模板。 */
    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
