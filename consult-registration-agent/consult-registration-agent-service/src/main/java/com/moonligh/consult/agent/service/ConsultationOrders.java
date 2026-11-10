package com.moonligh.consult.agent.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultOrderRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.api.res.PayPackageRes;
import com.moonligh.consult.agent.domain.BenefitLedgerRow;
import com.moonligh.consult.agent.domain.ConsultMinutes;
import com.moonligh.consult.agent.domain.ConsultOrder;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.MatchQueue;
import com.moonligh.consult.agent.domain.PayCatalog;
import com.moonligh.consult.agent.domain.benefit.BenefitQuote;
import com.moonligh.consult.agent.domain.pay.PayCommand;
import com.moonligh.consult.agent.domain.pay.PaymentTicket;
import com.moonligh.consult.agent.infrastructure.pay.ChannelPaymentCashier;
import com.moonligh.consult.agent.domain.safety.SafetyPolicy;
import com.moonligh.consult.agent.domain.triage.Certainty;
import com.moonligh.consult.agent.domain.triage.DecisionSource;
import com.moonligh.consult.agent.domain.triage.MatchDecision;
import com.moonligh.consult.agent.domain.triage.TriageNode;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

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
        return placeOrder(consultationId, packageCode, consultMinutes, null);
    }

    ConsultationRes placeOrder(String consultationId, Integer packageCode, Integer consultMinutes, String channel) {
        ConsultationRes placed = consult.repository.runLocked(
                consultationId, session -> placeOrderLocked(session, packageCode, consultMinutes, channel));
        placed = applyBenefit(placed);
        if (placed.order() == null || placed.order().payStatus() != PayStatus.PENDING) {
            return assignDoctor(placed);
        }
        boolean real = ChannelPaymentCashier.real(channel);
        if (!real) {
            return finishDemo(placed);
        }
        if (consult.cashier == null) {
            cancelPending(placed.consultationId(), placed.order().orderId());
            throw new BusinessException(ErrorCode.PAY_CHANNEL_NOT_READY);
        }
        PayCommand command = new PayCommand(
                placed.consultationId(),
                placed.order().orderId(),
                placed.order().payAmount(),
                "在线问诊-" + placed.order().packageName(),
                channel);
        try {
            PaymentTicket ticket = consult.cashier.collect(command);
            if (ticket != null && ticket.paid()) {
                if (!confirmPaid(placed.order().orderId(), placed.order().payAmount())) {
                    throw new BusinessException(ErrorCode.PAYMENT_PENDING);
                }
                return consult.repository.runLocked(placed.consultationId(), consult::buildResponse);
            }
            if (ticket == null || ticket.clientPayload() == null || ticket.clientPayload().isBlank()) {
                throw new BusinessException(ErrorCode.PAYMENT_PENDING);
            }
            return withLaunch(placed, ticket);
        } catch (RuntimeException error) {
            cancelPending(placed.consultationId(), placed.order().orderId());
            if (error instanceof BusinessException business) {
                throw business;
            }
            log.warn("支付下单失败 type={}", error.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.PAYMENT_PENDING);
        }
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
        ConsultOrderRes ended = view.order();
        if (consult.benefit != null && consult.benefit.enabled() && ended != null && ended.doctor() == null) {
            if (ended.payStatus() == PayStatus.PAID
                    && ended.benefitAmount() != null
                    && ended.benefitAmount().signum() > 0) {
                try {
                    consult.benefit.restore(ended.orderId());
                } catch (RuntimeException error) {
                    log.warn("权益退回失败 orderId={} type={}", ended.orderId(), error.getClass().getSimpleName());
                }
            } else if (ended.payStatus() == PayStatus.PENDING) {
                settleBenefit(ended.orderId(), false);
            }
        }
        return view;
    }

    ConsultationRes setConsultMinutes(String consultationId, Integer consultMinutes) {
        int minutes = ConsultMinutes.of(consultMinutes);
        return consult.repository.runLocked(consultationId, session -> {
            ConsultOrder order = session.getOrder();
            if (order == null) {
                throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
            }
            if (order.isEnded()) {
                throw new BusinessException(ErrorCode.CONSULT_ENDED);
            }
            if (order.consultMinutes() == minutes) {
                return consult.buildResponse(session);
            }
            if (consult.doctorAssigner != null) {
                consult.doctorAssigner.extend(order.orderId(), minutes);
            }
            session.setOrder(order.withMinutes(minutes));
            return consult.buildResponse(session);
        });
    }

    /** 已经拿到会话锁之后下单。急症和未确认都不能买。没有在线医生的科先改到全科。 */
    private ConsultationRes placeOrderLocked(
            ConsultationSession session, Integer packageCode, Integer consultMinutes, String channel) {
        consult.openSpecialty(session);
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
        boolean real = ChannelPaymentCashier.real(channel);
        boolean quote = consult.benefit != null && consult.benefit.enabled();
        ConsultOrder existing = session.getOrder();
        if (existing != null) {
            if (existing.packageCode() != packageCode) {
                throw new BusinessException(ErrorCode.ALREADY_ORDERED);
            }
            if (existing.payStatus() == PayStatus.PAID) {
                return consult.buildResponse(session);
            }
            if (existing.payStatus() == PayStatus.PENDING) {
                if (existing.isEnded()) {
                    session.setOrder(existing.reopen());
                }
                claimVisit(session);
                return consult.buildResponse(session);
            }
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
        if (real && consult.cashier == null) {
            throw new BusinessException(ErrorCode.PAY_CHANNEL_NOT_READY);
        }
        claimVisit(session);
        if (!real && !quote) {
            try {
                if (consult.cashier != null) {
                    PaymentTicket ticket = consult.cashier.collect(new PayCommand(
                            session.getId(), session.getId(), pkg.payPrice(), pkg.name(), "demo"));
                    if (ticket == null || !ticket.paid()) {
                        throw new BusinessException(ErrorCode.PAYMENT_PENDING);
                    }
                }
            } catch (RuntimeException error) {
                releaseVisit(session);
                throw error;
            }
            session.setOrder(ConsultOrder.paidOf(pkg, ConsultMinutes.of(consultMinutes)));
            return consult.buildResponse(session);
        }
        session.setOrder(ConsultOrder.pendingOf(pkg, ConsultMinutes.of(consultMinutes)));
        return consult.buildResponse(session);
    }

    /** 权益服务开着时，待支付订单先占额度，再决定现金。 */
    private ConsultationRes applyBenefit(ConsultationRes placed) {
        if (consult.benefit == null || !consult.benefit.enabled() || placed.order() == null
                || placed.order().payStatus() != PayStatus.PENDING) {
            return placed;
        }
        ConsultationSession snap = consult.repository.get(placed.consultationId());
        if (snap == null || snap.getOrder() == null || snap.getOrder().benefitReady()) {
            return placed;
        }
        String accountId = snap.getAccountId();
        if (accountId == null || accountId.isBlank()) {
            return consult.repository.runLocked(placed.consultationId(), session -> {
                ConsultOrder order = session.getOrder();
                if (order != null && order.payStatus() == PayStatus.PENDING && !order.benefitReady()) {
                    session.setOrder(order.applyBenefit(order.payAmount(), BigDecimal.ZERO, null));
                }
                return consult.buildResponse(session);
            });
        }
        BenefitQuote quote;
        try {
            quote = consult.benefit.reserve(
                    accountId, snap.getOrder().orderId(), snap.getOrder().packageCode(), snap.getOrder().payAmount());
        } catch (RuntimeException error) {
            cancelPending(placed.consultationId(), snap.getOrder().orderId());
            if (error instanceof BusinessException business) {
                throw business;
            }
            log.warn("权益预占失败 type={}", error.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.BENEFIT_UNAVAILABLE);
        }
        ConsultationRes applied = consult.repository.runLocked(placed.consultationId(), session -> {
            ConsultOrder order = session.getOrder();
            if (order == null || order.benefitReady() || order.payStatus() != PayStatus.PENDING || order.isEnded()) {
                return consult.buildResponse(session);
            }
            ConsultOrder next = order.applyBenefit(quote.cashDue(), quote.benefitAmount(), quote.cardId());
            if (quote.cashDue().signum() == 0) {
                next = next.markPaid(Instant.now());
            }
            session.setOrder(next);
            return consult.buildResponse(session);
        });
        if (applied.order() != null && applied.order().payStatus() == PayStatus.PAID) {
            settleBenefit(applied.order().orderId(), true);
        }
        return applied;
    }

    /** 权益抵完后仍要付的现金，演示渠道当场记已支付。 */
    private ConsultationRes finishDemo(ConsultationRes placed) {
        try {
            if (consult.cashier != null) {
                PaymentTicket ticket = consult.cashier.collect(new PayCommand(
                        placed.consultationId(),
                        placed.order().orderId(),
                        placed.order().payAmount(),
                        "在线问诊-" + placed.order().packageName(),
                        "demo"));
                if (ticket == null || !ticket.paid()) {
                    throw new BusinessException(ErrorCode.PAYMENT_PENDING);
                }
            }
            if (!confirmPaid(placed.order().orderId(), placed.order().payAmount())) {
                throw new BusinessException(ErrorCode.PAYMENT_PENDING);
            }
            return consult.repository.runLocked(placed.consultationId(), consult::buildResponse);
        } catch (RuntimeException error) {
            cancelPending(placed.consultationId(), placed.order().orderId());
            if (error instanceof BusinessException business) {
                throw business;
            }
            throw new BusinessException(ErrorCode.PAYMENT_PENDING);
        }
    }

    private void settleBenefit(String orderId, boolean confirm) {
        if (consult.benefit == null || !consult.benefit.enabled() || orderId == null || orderId.isBlank()) {
            return;
        }
        try {
            if (confirm) {
                consult.benefit.confirm(orderId);
            } else {
                consult.benefit.release(orderId);
            }
            consult.repository.markBenefitSettled(orderId);
        } catch (RuntimeException error) {
            log.warn("权益收尾失败 orderId={} confirm={} type={}", orderId, confirm, error.getClass().getSimpleName());
        }
    }

    /** 确认或退回没成功的单，过一会儿再对一次。两边都按状态幂等。 */
    int reconcileBenefits() {
        if (consult.benefit == null || !consult.benefit.enabled()) {
            return 0;
        }
        int settled = 0;
        for (BenefitLedgerRow row : consult.repository.listBenefitLedger(200)) {
            try {
                if (row.paid()) {
                    consult.benefit.confirm(row.orderId());
                } else if (row.cancelled()) {
                    consult.benefit.release(row.orderId());
                } else {
                    continue;
                }
                consult.repository.markBenefitSettled(row.orderId());
                settled++;
            } catch (RuntimeException error) {
                log.warn("权益对账失败 orderId={} type={}", row.orderId(), error.getClass().getSimpleName());
            }
        }
        return settled;
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
        if (consult.doctorAssigner == null || order == null || order.payStatus() != PayStatus.PAID
                || order.doctor() != null || order.endedAt() != null) {
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

    /**
     * 派单通知由身份服务在占坑同一事务里写出站表再投递。
     * 挂号服务不再发 ASSIGNED，避免同一订单两条消息。
     */
    private void syncAssigned(String consultationId, String orderId, AssignedDoctorRes doctor) {
        if (log.isDebugEnabled()) {
            log.debug("派单通知由身份服务投递 consultationId={} orderId={} doctorId={}",
                    consultationId, orderId, doctor == null ? null : doctor.id());
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
        releaseVisit(session);
        recordRelease(session, code);
        release.set(new Release(order.orderId(), code));
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

    /** 渠道回调验签之后调用。金额必须和目录价一致，重复通知不再派一次医生。 */
    boolean confirmPaid(String orderId, BigDecimal amountYuan) {
        if (orderId == null || orderId.isBlank() || amountYuan == null) {
            return false;
        }
        String consultationId = consult.repository.consultationIdOfOrder(orderId);
        if (consultationId == null) {
            return false;
        }
        AtomicBoolean newlyPaid = new AtomicBoolean();
        ConsultationRes view = consult.repository.runLocked(consultationId, session -> {
            ConsultOrder order = session.getOrder();
            if (order == null || !sameOrder(order.orderId(), orderId)) {
                return consult.buildResponse(session);
            }
            if (order.payStatus() == PayStatus.PAID) {
                return consult.buildResponse(session);
            }
            BigDecimal expected = order.payAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal actual = amountYuan.setScale(2, RoundingMode.HALF_UP);
            if (expected.compareTo(actual) != 0) {
                throw new BusinessException(ErrorCode.PAYMENT_PENDING);
            }
            session.setOrder(order.markPaid(Instant.now()));
            try {
                claimVisit(session);
            } catch (BusinessException busy) {
                log.warn("支付已到账，就诊人占用未加上 orderId={}", order.orderId());
            }
            newlyPaid.set(true);
            return consult.buildResponse(session);
        });
        if (newlyPaid.get()) {
            settleBenefit(orderId, true);
            assignDoctor(view);
        }
        return true;
    }

    private void cancelPending(String consultationId, String orderId) {
        AtomicBoolean cancelled = new AtomicBoolean();
        consult.repository.runLocked(consultationId, session -> {
            ConsultOrder order = session.getOrder();
            if (order != null
                    && sameOrder(order.orderId(), orderId)
                    && order.payStatus() == PayStatus.PENDING
                    && !order.isEnded()) {
                session.setOrder(order.ended(Instant.now(), "CANCEL"));
                releaseVisit(session);
                cancelled.set(true);
            }
            return consult.buildResponse(session);
        });
        if (cancelled.get()) {
            settleBenefit(orderId, false);
        }
    }

    private static ConsultationRes withLaunch(ConsultationRes view, PaymentTicket ticket) {
        ConsultOrderRes order = view.order();
        ConsultOrderRes launched = new ConsultOrderRes(
                order.orderId(),
                order.packageCode(),
                order.packageName(),
                order.originAmount(),
                order.payAmount(),
                order.discountAmount(),
                order.payStatus(),
                order.paidAt(),
                order.doctor(),
                order.consultMinutes(),
                order.endsAt(),
                order.endedAt(),
                order.endReason(),
                ticket.channel(),
                ticket.clientPayload(),
                order.benefitAmount());
        return new ConsultationRes(
                view.consultationId(),
                view.status(),
                view.prompt(),
                view.missingSlot(),
                view.card(),
                view.safetyNotice(),
                view.handoff(),
                view.packages(),
                launched);
    }

    private static boolean sameOrder(String stored, String given) {
        if (stored == null || given == null) {
            return false;
        }
        return stored.equals(given) || stored.replace("-", "").equals(given.replace("-", ""));
    }

    /** 支付成交前占住账号和就诊人。别的咨询已经占住就拒绝本单。 */
    private void claimVisit(ConsultationSession session) {
        String accountId = session.getAccountId();
        String patientId = session.getPatientId();
        if (accountId == null || accountId.isBlank() || patientId == null || patientId.isBlank()) {
            return;
        }
        if (!consult.activeVisits.claim(accountId, patientId, session.getId())) {
            throw new BusinessException(ErrorCode.CONSULT_PATIENT_BUSY);
        }
    }

    /** 问诊结束后换这位就诊人可以再发起。 */
    private void releaseVisit(ConsultationSession session) {
        String accountId = session.getAccountId();
        String patientId = session.getPatientId();
        if (accountId == null || accountId.isBlank() || patientId == null || patientId.isBlank()) {
            return;
        }
        consult.activeVisits.release(accountId, patientId, session.getId());
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
