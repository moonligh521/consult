package com.moonligh.consult.agent.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moonligh.consult.agent.api.event.OrderAssignedEvent;
import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultOrderRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.api.res.PayPackageRes;
import com.moonligh.consult.agent.domain.ConsultMinutes;
import com.moonligh.consult.agent.domain.ConsultOrder;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.MatchQueue;
import com.moonligh.consult.agent.domain.PayCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyPolicy;
import com.moonligh.consult.agent.domain.triage.Certainty;
import com.moonligh.consult.agent.domain.triage.DecisionSource;
import com.moonligh.consult.agent.domain.triage.MatchDecision;
import com.moonligh.consult.agent.domain.triage.TriageNode;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.mq.ConsultMqTopics;

/**
 * 付钱之后：落单、占坑、排队、结束问诊。入口仍是 {@link ConsultationService}，判断与原来相同。
 */
final class ConsultationOrders {

    private static final Logger log = LoggerFactory.getLogger(ConsultationOrders.class);

    private final ConsultationService consult;

    ConsultationOrders(ConsultationService consult) {
        this.consult = consult;
    }

    ConsultationRes placeOrder(String consultationId, Integer packageCode, Integer consultMinutes) {
        rejectIfNoDuty(consultationId, packageCode);
        ConsultationRes placed = consult.repository.runLocked(
                consultationId, session -> placeOrderLocked(session, packageCode, consultMinutes));
        return assignDoctor(placed);
    }

    ConsultationRes reassign(String consultationId) {
        return assignDoctor(consult.repository.runLocked(consultationId, this::reassignLocked));
    }

    int drainMatchQueue() {
        return consult.matchDispatcher == null ? 0 : consult.matchDispatcher.promoteDue();
    }

    void rebuildMatchQueue() {
        reconcileMatchQueue();
    }

    int reconcileMatchQueue() {
        if (consult.matchDispatcher == null) {
            return 0;
        }
        return consult.matchDispatcher.reconcile(consult.repository.listUnmatchedPaid(5000));
    }

    boolean occupyQueued(MatchQueue.Entry entry) {
        if (entry == null || consult.doctorAssigner == null) {
            return false;
        }
        ConsultationSession snapshot = consult.repository.get(entry.consultationId());
        if (snapshot == null || snapshot.getOrder() == null) {
            return true;
        }
        ConsultOrder order = snapshot.getOrder();
        if (order.doctor() != null || order.isEnded() || !entry.orderId().equals(order.orderId())) {
            return true;
        }
        List<String> terms = canonicalTerms(snapshot);
        int grade = entry.queuedGrade() <= 0 ? order.packageCode() : entry.queuedGrade();
        AssignedDoctorRes assigned = tryAssign(snapshot, order, terms, grade, false);
        if (assigned == null) {
            assigned = tryAssign(snapshot, order, terms, grade, true);
        }
        if (assigned == null) {
            return false;
        }
        AssignedDoctorRes doctor = assigned;
        AtomicReference<Release> release = new AtomicReference<>();
        AtomicReference<AssignedDoctorRes> attached = new AtomicReference<>();
        consult.repository.runLocked(
                snapshot.getId(),
                session -> attachDoctorLocked(session, order.orderId(), doctor, terms, release, attached));
        Release pending = release.get();
        if (pending != null) {
            consult.doctorAssigner.release(pending.orderId(), pending.reason());
        } else {
            syncAssigned(snapshot.getId(), order.orderId(), attached.get());
        }
        return true;
    }

    ConsultationRes endConsult(String consultationId, String reason) {
        AtomicReference<Release> release = new AtomicReference<>();
        ConsultationRes view = consult.repository.runLocked(
                consultationId, session -> endConsultLocked(session, reason, release));
        Release pending = release.get();
        AssignedDoctorRes freed = view.order() == null ? null : view.order().doctor();
        if (pending != null && consult.doctorAssigner != null) {
            try {
                consult.doctorAssigner.release(pending.orderId(), pending.reason());
            } catch (RuntimeException error) {
                // 会话结束态已落库；占坑释放失败由身份侧超时扫描兜底，不能挡住结束
            }
            if (consult.matchDispatcher != null && freed != null) {
                consult.matchDispatcher.onSlotFreed(
                        freed.specialty(), MatchQueue.gradeOfTitle(freed.title()));
            }
        }
        return view;
    }

    ConsultationRes setConsultMinutes(String consultationId, Integer consultMinutes) {
        ConsultationSession snapshot = consultationId == null || consultationId.isBlank()
                ? null
                : consult.repository.get(consultationId);
        ConsultOrder order = snapshot == null ? null : snapshot.getOrder();
        if (order != null && !order.isEnded() && consult.doctorAssigner != null) {
            consult.doctorAssigner.extend(order.orderId(), ConsultMinutes.of(consultMinutes));
        }
        return consult.repository.runLocked(
                consultationId, session -> setConsultMinutesLocked(session, consultMinutes));
    }

    private void rejectIfNoDuty(String consultationId, Integer packageCode) {
        if (consult.doctorAssigner == null || consultationId == null || consultationId.isBlank()) {
            return;
        }
        ConsultationSession session = consult.repository.get(consultationId);
        if (session == null || session.getOrder() != null) {
            return;
        }
        int code = packageCode == null ? 0 : packageCode;
        if (!consult.doctorAssigner.hasOnDuty(session.getCard(), code)) {
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
    }

    /** 已经拿到会话锁之后下单。急症和未确认都不能买。 */
    private ConsultationRes placeOrderLocked(
            ConsultationSession session, Integer packageCode, Integer consultMinutes) {
        SafetyPolicy.rejectIfSafetyStop(session);
        consult.rejectIfInquiryClosed();
        if (session.getBusinessStatus() != ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
        consult.requireLiveCard(session.getCard());
        if (packageCode == null) {
            throw new BusinessException(ErrorCode.INVALID_PACKAGE);
        }
        PayPackageRes pkg = PayCatalog.find(packageCode);
        if (pkg == null) {
            throw new BusinessException(ErrorCode.INVALID_PACKAGE);
        }
        ConsultOrder existing = session.getOrder();
        if (existing != null) {
            if (existing.packageCode() == packageCode) {
                return consult.buildResponse(session);
            }
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
        session.setOrder(ConsultOrder.paidOf(pkg, ConsultMinutes.of(consultMinutes)));
        return consult.buildResponse(session);
    }

    private ConsultationRes reassignLocked(ConsultationSession session) {
        ConsultOrder order = session.getOrder();
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
        if (order.isEnded()) {
            throw new BusinessException(ErrorCode.CONSULT_ENDED);
        }
        if (order.doctor() == null && consult.doctorAssigner == null) {
            throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
        }
        return consult.buildResponse(session);
    }

    /** 订单已落库、还没医生：锁外调身份服务派单，再短锁写回。同一科按支付时间先进先出。 */
    private ConsultationRes assignDoctor(ConsultationRes placed) {
        ConsultOrderRes order = placed.order();
        if (consult.doctorAssigner == null || order == null || order.doctor() != null || order.endedAt() != null) {
            return placed;
        }
        String consultationId = placed.consultationId();
        ConsultationSession snapshot = consult.repository.get(consultationId);
        if (snapshot == null || snapshot.getOrder() == null) {
            return placed;
        }
        if (consult.matchDispatcher != null) {
            consult.matchDispatcher.offer(queueEntry(snapshot));
            ConsultationSession latest = consult.repository.get(consultationId);
            return latest == null ? placed : consult.buildResponse(latest);
        }
        List<String> terms = canonicalTerms(snapshot);
        ConsultOrder sessionOrder = snapshot.getOrder();
        int grade = sessionOrder.packageCode();
        AssignedDoctorRes assigned = tryAssign(snapshot, sessionOrder, terms, grade, false);
        if (assigned == null) {
            assigned = tryAssign(snapshot, sessionOrder, terms, grade, true);
        }
        if (assigned == null) {
            return placed;
        }
        AssignedDoctorRes doctor = assigned;
        AtomicReference<Release> release = new AtomicReference<>();
        AtomicReference<AssignedDoctorRes> attached = new AtomicReference<>();
        ConsultationRes view = consult.repository.runLocked(
                consultationId,
                session -> attachDoctorLocked(session, order.orderId(), doctor, terms, release, attached));
        Release pending = release.get();
        if (pending != null) {
            try {
                consult.doctorAssigner.release(pending.orderId(), pending.reason());
            } catch (RuntimeException error) {
                // 派单途中问诊已结束：占坑交给身份侧超时扫描兜底
            }
        } else {
            syncAssigned(consultationId, order.orderId(), attached.get());
        }
        return view;
    }

    private static MatchQueue.Entry queueEntry(ConsultationSession session) {
        ConsultOrder order = session.getOrder();
        String specialty = session.getCard() == null ? "" : session.getCard().specialty();
        return new MatchQueue.Entry(
                session.getId(),
                order.orderId(),
                order.paidAt(),
                order.packageCode(),
                specialty == null ? "" : specialty);
    }

    private ConsultationRes attachDoctorLocked(
            ConsultationSession session,
            String orderId,
            AssignedDoctorRes doctor,
            List<String> terms,
            AtomicReference<Release> release,
            AtomicReference<AssignedDoctorRes> attached) {
        ConsultOrder current = session.getOrder();
        if (current == null || !orderId.equals(current.orderId()) || current.doctor() != null) {
            return consult.buildResponse(session);
        }
        if (current.isEnded()) {
            release.set(new Release(orderId, current.endReason()));
            return consult.buildResponse(session);
        }
        session.setOrder(current.withDoctor(doctor));
        if (attached != null) {
            attached.set(doctor);
        }
        recordAssign(session, orderId, doctor, terms);
        return consult.buildResponse(session);
    }

    /** 本地已占坑后再通知医生端。没接 Broker 或发送失败不影响患者侧。 */
    private void syncAssigned(String consultationId, String orderId, AssignedDoctorRes doctor) {
        if (consult.mqSender == null || doctor == null || orderId == null || orderId.isBlank()) {
            return;
        }
        try {
            consult.mqSender.syncSendJson(
                    ConsultMqTopics.orderAssigned(),
                    new OrderAssignedEvent(
                            orderId, consultationId, doctor.id(), doctor.specialty(), doctor.scheduleId()),
                    orderId);
        } catch (RuntimeException error) {
            log.warn("订单同步医生端失败 orderId={}", orderId);
        }
    }

    private ConsultationRes endConsultLocked(
            ConsultationSession session, String reason, AtomicReference<Release> release) {
        ConsultOrder order = session.getOrder();
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
        // 已结束幂等：占坑身份侧也是非 ACTIVE，超时扫描不会再扫到
        if (order.isEnded()) {
            return consult.buildResponse(session);
        }
        // 前端手动结束传 ENDED；倒计时传 TIMEOUT；未传且已过期按 TIMEOUT
        String code;
        if (reason != null && !reason.isBlank()) {
            code = reason.trim().toUpperCase();
        } else if (order.endsAt() != null && Instant.now().isAfter(order.endsAt())) {
            code = "TIMEOUT";
        } else {
            code = "ENDED";
        }
        // 先落 ended_at + end_reason，再释放占坑。身份侧 status≠ACTIVE 后 XXL 不再扫本单。
        session.setOrder(order.ended(Instant.now(), code));
        recordRelease(session, code);
        release.set(new Release(order.orderId(), code));
        return consult.buildResponse(session);
    }

    private ConsultationRes setConsultMinutesLocked(ConsultationSession session, Integer consultMinutes) {
        ConsultOrder order = session.getOrder();
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
        if (order.isEnded()) {
            throw new BusinessException(ErrorCode.CONSULT_ENDED);
        }
        int minutes = ConsultMinutes.of(consultMinutes);
        session.setOrder(order.withMinutes(minutes));
        return consult.buildResponse(session);
    }

    private AssignedDoctorRes tryAssign(
            ConsultationSession snapshot,
            ConsultOrder order,
            List<String> terms,
            int grade,
            boolean allowUpgrade) {
        try {
            return consult.doctorAssigner.assign(
                    snapshot.getId(),
                    order.orderId(),
                    snapshot.getCard(),
                    terms,
                    order.consultMinutes(),
                    grade,
                    allowUpgrade);
        } catch (BusinessException error) {
            if (error.getErrorCode() == ErrorCode.DOCTOR_UNAVAILABLE) {
                return null;
            }
            throw error;
        }
    }

    /** 派单用的归约病名。只读已存结论；老会话没有时纯规则现算，支付路径不调模型。 */
    private List<String> canonicalTerms(ConsultationSession session) {
        if (session.getTriage() != null) {
            return session.getTriage().canonicalTerms();
        }
        ConsultationCardRes card = session.getCard();
        if (card == null || card.description() == null) {
            return List.of();
        }
        return consult.registrationAgent.engine().canon().rule(card.description()).termNames();
    }

    /** N8 派医生 + N9 占坑。 */
    private void recordAssign(
            ConsultationSession session, String orderId, AssignedDoctorRes doctor, List<String> terms) {
        String runId = session.getTriage() == null ? UUID.randomUUID().toString() : session.getTriage().runId();
        String matchSource = doctor == null || doctor.matchSource() == null ? "UNKNOWN" : doctor.matchSource();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("doctorId", doctor == null ? null : doctor.id());
        output.put("specialty", doctor == null ? null : doctor.specialty());
        output.put("canonicalTerms", terms);
        output.put("skillHit", doctor == null ? null : doctor.evidence());
        boolean skill = matchSource.startsWith("SKILL_");
        consult.record(session, MatchDecision.of(runId, TriageNode.N8_DOCTOR, skill ? Certainty.CERTAIN : Certainty.UNCERTAIN,
                skill ? DecisionSource.RULE : DecisionSource.FALLBACK, matchSource, output, List.of(), 0),
                orderId, MatchDecision.TRIGGER_ASSIGN);
        consult.record(session, MatchDecision.of(runId, TriageNode.N9_OCCUPY, Certainty.CERTAIN, DecisionSource.RULE,
                doctor == null ? "NO_DOCTOR" : "OCCUPIED",
                Map.of("scheduleId", doctor == null || doctor.scheduleId() == null ? "" : doctor.scheduleId()),
                List.of(), 0), orderId, MatchDecision.TRIGGER_ASSIGN);
    }

    private void recordRelease(ConsultationSession session, String reason) {
        ConsultOrder order = session.getOrder();
        String runId = session.getTriage() == null ? UUID.randomUUID().toString() : session.getTriage().runId();
        consult.record(session, MatchDecision.of(runId, TriageNode.N9_OCCUPY, Certainty.CERTAIN, DecisionSource.RULE,
                reason, Map.of("orderId", order == null ? "" : order.orderId()), List.of(), 0),
                order == null ? null : order.orderId(), MatchDecision.TRIGGER_ASSIGN);
    }

    /** 锁内定下要释放的占坑，提交后再调身份服务。 */
    private record Release(String orderId, String reason) {
    }
}
