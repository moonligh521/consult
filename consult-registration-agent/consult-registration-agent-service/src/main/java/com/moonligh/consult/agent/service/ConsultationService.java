package com.moonligh.consult.agent.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.ConsultOrderRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationDetailRes;
import com.moonligh.consult.agent.api.res.ConsultationPageRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.api.res.PayPackageRes;
import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.CardFacts;
import com.moonligh.consult.agent.domain.ComplaintClarity;
import com.moonligh.consult.agent.domain.ConsultOrder;
import com.moonligh.consult.agent.domain.ConsultationRepository;
import com.moonligh.consult.agent.domain.MatchQueue;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.RegistrationAgent;
import com.moonligh.consult.agent.domain.PayCatalog;
import com.moonligh.consult.agent.domain.SessionAction;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.Slots;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.agent.domain.safety.AuditTrigger;
import com.moonligh.consult.agent.domain.safety.ConsultationMessage;
import com.moonligh.consult.agent.domain.safety.Demographics;
import com.moonligh.consult.agent.domain.safety.EvaluationKind;
import com.moonligh.consult.agent.domain.safety.MessageSource;
import com.moonligh.consult.framework.model.ModelStatus;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SafetyAssessment;
import com.moonligh.consult.agent.domain.safety.SafetyCatalog;
import com.moonligh.consult.agent.domain.safety.SafetyDecisionAudit;
import com.moonligh.consult.agent.domain.safety.SafetyGate;
import com.moonligh.consult.agent.domain.safety.SafetyMetrics;
import com.moonligh.consult.agent.domain.safety.SafetyPolicy;
import com.moonligh.consult.agent.domain.patient.MemoryPatientStore;
import com.moonligh.consult.agent.domain.patient.PatientRecord;
import com.moonligh.consult.agent.domain.patient.PatientStore;
import com.moonligh.consult.agent.domain.triage.Certainty;
import com.moonligh.consult.agent.domain.triage.DecisionSource;
import com.moonligh.consult.agent.domain.triage.MatchDecision;
import com.moonligh.consult.agent.domain.triage.RouteDecision;
import com.moonligh.consult.agent.domain.triage.TriageInput;
import com.moonligh.consult.agent.domain.triage.TriageNode;
import com.moonligh.consult.agent.domain.triage.TriageResult;
import com.moonligh.consult.agent.domain.triage.TriageRun;
import com.moonligh.consult.agent.domain.triage.TurnSnapshot;
import com.moonligh.consult.agent.domain.triage.AgentTraceZh;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.observability.AgentSpan;
import com.moonligh.consult.framework.observability.AgentTrace;
import com.moonligh.consult.framework.observability.AgentTraceContext;
import com.moonligh.consult.framework.observability.AgentTraceNames;
import com.moonligh.consult.framework.mq.ConsultMqSender;

/**
 * 咨询编排：先存原话、先做安全评估，再走挂号业务。
 */
@Service
public class ConsultationService {

    private static final Logger log = LoggerFactory.getLogger(ConsultationService.class);

    /** 当前请求的模型结论暂存。只在 runWithModelsOutsideLock 期间有值。 */
    private static final ThreadLocal<ModelMemo> MEMO = new ThreadLocal<>();

    /** 会话存取。同一咨询编号会排队，避免两条消息互相覆盖。 */
    final ConsultationRepository repository;
    /** 安全闸门：先规则，必要时再让模型补漏。 */
    private final SafetyGate safetyGate;
    /** 从一句话里抽性别、年龄、病程。 */
    private final SlotParser slotParser;
    /** 从病情描述里抽症状标签。 */
    private final SymptomExtractor symptomExtractor;
    /** 工具型挂号 Agent：纠错+关键词快路径，对不上才允许模型补科室。 */
    final RegistrationAgent registrationAgent;
    /** 安全状态计数，给监测用，不含病情原文。 */
    private final SafetyMetrics metrics;
    /** 判断病情描述是否太含糊。 */
    private final ComplaintClarity complaintClarity;
    /** 问诊通道关闭时不能确认、不能下单。 */
    final InquiryProperties inquiry;
    /** 账号下的就诊人档案。绑到本轮咨询时写入性别、年龄和姓名。 */
    private final PatientStore patients;
    /** 支付后按二级科和擅长派医生。测试可不接。 */
    final DoctorAssigner doctorAssigner;
    /** Redis/内存派单队。测试可不接。 */
    MatchDispatcher matchDispatcher;
    /** 派单成功后同步医生端。没配 name-server 时不接。 */
    ConsultMqSender mqSender;
    private AgentTrace agentTrace = AgentTrace.NOOP;
    /** 付钱之后的落单、占坑、排队。判断仍走原来的路径。 */
    private final ConsultationOrders orders;

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            DepartmentRouter departmentRouter,
            SafetyMetrics metrics) {
        this(repository, safetyGate, slotParser, symptomExtractor, departmentRouter, metrics, new InquiryProperties(), new MemoryPatientStore());
    }

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            DepartmentRouter departmentRouter,
            SafetyMetrics metrics,
            InquiryProperties inquiry) {
        this(repository, safetyGate, slotParser, symptomExtractor, departmentRouter, metrics, inquiry, new MemoryPatientStore());
    }

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            DepartmentRouter departmentRouter,
            SafetyMetrics metrics,
            InquiryProperties inquiry,
            PatientStore patients) {
        this(repository, safetyGate, slotParser, symptomExtractor, new RegistrationAgent(departmentRouter), metrics, inquiry, patients, null);
    }

    @Autowired
    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            RegistrationAgent registrationAgent,
            SafetyMetrics metrics,
            InquiryProperties inquiry,
            PatientStore patients,
            DoctorAssigner doctorAssigner) {
        this.repository = repository;
        this.safetyGate = safetyGate;
        this.slotParser = slotParser;
        this.symptomExtractor = symptomExtractor;
        this.registrationAgent = registrationAgent;
        this.metrics = metrics;
        this.complaintClarity = new ComplaintClarity(symptomExtractor, slotParser);
        this.inquiry = inquiry == null ? new InquiryProperties() : inquiry;
        this.patients = patients == null ? new MemoryPatientStore() : patients;
        this.doctorAssigner = doctorAssigner;
        this.orders = new ConsultationOrders(this);
    }

    @Autowired(required = false)
    public void setMatchDispatcher(MatchDispatcher matchDispatcher) {
        this.matchDispatcher = matchDispatcher;
    }

    @Autowired(required = false)
    public void setAgentTrace(AgentTrace agentTrace) {
        this.agentTrace = agentTrace == null ? AgentTrace.NOOP : agentTrace;
    }

    @Autowired(required = false)
    public void setMqSender(ConsultMqSender mqSender) {
        this.mqSender = mqSender;
    }

    @jakarta.annotation.PostConstruct
    void logMq() {
        if (mqSender == null) {
            log.info("未接 RocketMQ，订单指派只写本库并通过身份服务 HTTP 占坑");
        }
    }

    /** 简写入口：不传 messageId。 */
    public ConsultationRes acceptMessage(String consultationId, String text) {
        return acceptMessage(consultationId, null, text);
    }

    /**
     * 收一句病情。本产品只做在线问诊的智能挂号。
     */
    public ConsultationRes acceptMessage(String consultationId, String messageId, String text) {
        return acceptMessage(consultationId, messageId, text, null);
    }

    public ConsultationRes acceptMessage(String consultationId, String messageId, String text, String accountId) {
        // 幂等键最长 64 个字符，超了直接参数错误。
        if (messageId != null && messageId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        // 同一咨询排队执行；模型先在锁外算好，锁内只办事、保存。
        return runWithModelsOutsideLock(consultationId, session -> acceptLocked(session, messageId, text, accountId));
    }

    /**
     * 先在会话副本上预演一遍，把安全模型和主诉模型的结论算好；再进锁用同样输入复用结论。
     * 这样数据库行锁和连接只在毫秒级的读改写期间占用，不陪着模型等几秒。
     */
    private ConsultationRes runWithModelsOutsideLock(String consultationId, SessionAction action) {
        boolean creating = consultationId == null || consultationId.isBlank();
        String id = creating ? repository.reserveNew() : consultationId;
        ConsultationSession draft = creating ? new ConsultationSession(id) : draftOf(id);
        if (draft == null) {
            return repository.runLocked(id, action);
        }
        ModelMemo memo = new ModelMemo();
        MEMO.set(memo);
        try {
            action.run(draft);
        } catch (RuntimeException ignored) {
            // 预演只为提前拿到模型结论；真正的报错由锁内那一遍给出
        } finally {
            MEMO.remove();
        }
        MEMO.set(memo.replay());
        try {
            return repository.runLocked(id, action);
        } finally {
            MEMO.remove();
        }
    }

    /** 已有编号读一份不加锁的副本。找不到就不预演，交给锁内报 1101。 */
    private ConsultationSession draftOf(String consultationId) {
        ConsultationSession current = repository.get(consultationId);
        return current == null ? null : current.copy();
    }

    /** 当前账号一年内咨询记录。 */
    public ConsultationPageRes listMine(String accountId, int page, int size) {
        String owner = requireAccount(accountId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        Instant from = Instant.now().minus(365, ChronoUnit.DAYS);
        long total = repository.countMine(owner, from);
        return new ConsultationPageRes(
                total,
                safePage,
                safeSize,
                repository.listMine(owner, from, (safePage - 1) * safeSize, safeSize));
    }

    /** 打开一条自己的咨询记录。 */
    public ConsultationDetailRes getMine(String accountId, String consultationId) {
        String owner = requireAccount(accountId);
        ConsultationSession session = repository.get(consultationId);
        assertReadable(session, owner);
        String title = session.getTitle();
        if (title == null || title.isBlank()) {
            title = clipTitle(session.getDescription());
        }
        return new ConsultationDetailRes(buildResponse(session), title, session.getTranscript());
    }

    /**
     * 患者等派医生。带 JWT 的长轮询，一次最多 10 秒，避免网关 15 秒超时。
     * 已派上、已结束、还没订单立刻返回。
     */
    public ConsultationDetailRes waitMine(String accountId, String consultationId, long waitMs) {
        long cap = Math.min(10_000L, Math.max(0L, waitMs));
        long deadline = System.currentTimeMillis() + cap;
        ConsultationDetailRes latest = getMine(accountId, consultationId);
        while (!assignedOrSettled(latest) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return latest;
            }
            latest = getMine(accountId, consultationId);
        }
        return latest;
    }

    private static boolean assignedOrSettled(ConsultationDetailRes detail) {
        if (detail == null || detail.view() == null || detail.view().order() == null) {
            return true;
        }
        ConsultOrderRes order = detail.view().order();
        return order.doctor() != null || order.endedAt() != null;
    }

    /** 把诊室画面存回会话。 */
    public ConsultationRes saveTranscript(String consultationId, String accountId, String title, String transcript) {
        if (consultationId == null || consultationId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        String owner = requireAccount(accountId);
        return repository.runLocked(consultationId, session -> {
            bindOwner(session, owner);
            if (title != null && !title.isBlank()) {
                session.setTitle(clipTitle(title));
            }
            if (transcript != null) {
                session.setTranscript(transcript);
            }
            return buildResponse(session);
        });
    }

    /** 订单上还没有医生时再派一次。排队超时的队头会提档。 */
    public ConsultationRes reassign(String consultationId) {
        return orders.reassign(consultationId);
    }

    /**
     * 把排队超过 1 分钟的队头提一档。不扫全表，只消化延迟集合里到期的单。
     */
    public int drainMatchQueue() {
        return orders.drainMatchQueue();
    }

    /** 启动时把库里未派上的单重新入队。 */
    public void rebuildMatchQueue() {
        orders.rebuildMatchQueue();
    }

    /**
     * 对账：MySQL 已支付未派医生，Redis 队里没有的补进去，再按科/档试占坑。
     * 覆盖弹出后进程死了、放号消息丢了。
     */
    public int reconcileMatchQueue() {
        return orders.reconcileMatchQueue();
    }

    /** 队头出队后占坑。没坑返回 false，调用方把单放回队头。 */
    public boolean occupyQueued(MatchQueue.Entry entry) {
        return orders.occupyQueued(entry);
    }

    /**
     * 修改已生成的卡片。急症、转人工、安全降级时不能改。
     */
    public ConsultationRes updateCard(CardPatchReq patch) {
        if (patch == null) {
            throw new BusinessException(ErrorCode.EMPTY_CARD_PATCH);
        }
        return runWithModelsOutsideLock(patch.consultationId(), session -> updateLocked(session, patch));
    }

    /**
     * 患者确认卡片。确认后再来新病情，仍先做安全评估。
     */
    public ConsultationRes confirm(String consultationId) {
        return repository.runLocked(consultationId, this::confirmLocked);
    }

    /** 下单页四档，不依赖某一轮咨询。 */
    public List<PayPackageRes> packages() {
        return PayCatalog.list();
    }

    /**
     * 卡片确认后选择档位。演示环境直接记已支付，不走微信/支付宝。
     */
    public ConsultationRes placeOrder(String consultationId, Integer packageCode) {
        return placeOrder(consultationId, packageCode, null);
    }

    /**
     * 先在锁内落订单（订单号就此固定），提交后再到锁外派医生。
     * 当前时段没号不收款。派单失败时订单已在库里，再次下同一档或调重派都用同一个订单号。
     */
    public ConsultationRes placeOrder(String consultationId, Integer packageCode, Integer consultMinutes) {
        return orders.placeOrder(consultationId, packageCode, consultMinutes);
    }

    /** 患者结束问诊，释放医生占坑。结束态先提交，释放在锁外做。 */
    public ConsultationRes endConsult(String consultationId, String reason) {
        return orders.endConsult(consultationId, reason);
    }

    /** 问诊中改时长，从现在起重新计时。身份侧延时在锁外做，按订单号幂等。 */
    public ConsultationRes setConsultMinutes(String consultationId, Integer consultMinutes) {
        return orders.setConsultMinutes(consultationId, consultMinutes);
    }

    /**
     * 把账号下的一位就诊人绑到本轮咨询。性别、年龄以档案为准，病程仍从主诉抽。
     */
    public ConsultationRes bindPatient(String consultationId, String patientId, String accountId) {
        if (consultationId == null || consultationId.isBlank() || patientId == null || patientId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_MISSING);
        }
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
        PatientRecord patient = patients.find(accountId.trim(), patientId);
        if (patient == null) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_FOUND);
        }
        return runWithModelsOutsideLock(consultationId, session -> {
            bindOwner(session, accountId.trim());
            return bindLocked(session, patient);
        });
    }

    /**
     * 已经拿到会话锁之后收消息。
     */
    private ConsultationRes acceptLocked(
            ConsultationSession session,
            String messageId,
            String text,
            String accountId) {
        bindOwner(session, accountId);
        bindTrace(session);
        String spoken = text == null ? "" : text.trim();
        try (AgentSpan turn = beginTurn()) {
            try {
                // 问诊已结束：会话关闭，患者和医生侧都不可再发。
                if (session.getOrder() != null && session.getOrder().isEnded()) {
                    throw new BusinessException(ErrorCode.CONSULT_ENDED);
                }
                // 同一个 messageId 再来一次：不重复入库，直接返回当前画面（含档位和订单）。
                if (messageId != null && session.hasMessage(messageId)) {
                    return explainTurn(turn, "DUPLICATE", "IDEMPOTENT", spoken, buildResponse(session));
                }
                // 去掉首尾空格。全是空格就当成没说话。
                if (spoken.isEmpty()) {
                    // 已经急症/转人工/降级时，空句也只回安全画面。
                    ConsultationRes blank = SafetyPolicy.isSafetyStop(session)
                            ? SafetyPolicy.view(session, catalog())
                            : blankView(session);
                    return explainTurn(turn, "BLANK", "NEED_DESCRIPTION", spoken, blank);
                }
                if (session.getTitle() == null || session.getTitle().isBlank()) {
                    session.setTitle(clipTitle(spoken));
                }
                // 原话只追加，不覆盖以前说过的话。
                session.appendMessage(messageId, MessageSource.MESSAGE, spoken);
                // 用全部原话做安全评估；已急症则钉死。
                SafetyAssessment safety = assess(session, AuditTrigger.MESSAGE);
                // 本轮升到急症、转人工、降级：先回安全画面，不抛 1104/1105。
                if (SafetyPolicy.isSafetyStop(session)) {
                    SafetyPolicy.applyStopCardPolicy(session);
                    return explainTurn(
                            turn, "SAFETY_STOP", session.getBusinessStatus().name(), spoken, buildResponse(session));
                }
                // 已确认且没有安全升级：普通消息返回 1105。原话已经入库。
                if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
                    throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
                }
                // 出卡后不能再对话补述，改信息只走卡片修改。
                if (session.getBusinessStatus() == ConsultationStatus.CARD_READY) {
                    throw new BusinessException(ErrorCode.CARD_PENDING_CONFIRMATION);
                }
                // 第一句只说「有点不舒服」：先追问具体病情，不进入选就诊人。
                if (shouldAskForDetail(session, spoken, safety)) {
                    session.setBusinessStatus(ConsultationStatus.NEED_DESCRIPTION);
                    session.setCard(null);
                    return explainTurn(turn, "N2_VAGUE", "VAGUE_ASK", spoken, buildResponse(session));
                }
                // 空着的槽位才从这句里抽，已经有的不覆盖。
                fillEmptySlots(session, spoken);
                // 卡片展示用的描述另存一份，和原话分开。
                addDescription(session, spoken);
                // 缺槽位就追问；齐了就出卡片。
                ConsultationRes view = nextView(session, MatchDecision.TRIGGER_CARD);
                if (view.missingSlot() != null) {
                    return explainTurn(turn, "N3_SLOT", "ASK_" + view.missingSlot().name(), spoken, view);
                }
                return explainTurn(turn, "N6_DEPARTMENT", "SLOTS_COMPLETE", spoken, view);
            } catch (BusinessException ex) {
                explainReject(turn, spoken, ex);
                throw ex;
            }
        } finally {
            agentTrace.clear();
        }
    }

    /**
     * 已经拿到会话锁之后改卡片。只改传入的字段。
     */
    private ConsultationRes updateLocked(ConsultationSession session, CardPatchReq patch) {
        bindTrace(session);
        try (AgentSpan turn = beginTurn()) {
            try {
                ConsultationRes view = patchCardLocked(session, patch);
                if (SafetyPolicy.isSafetyStop(session)) {
                    return explainTurn(turn, "SAFETY_STOP", session.getBusinessStatus().name(), "改卡", view);
                }
                return explainTurn(turn, "PATCH_CARD", patchFields(patch), "改卡", view);
            } catch (BusinessException ex) {
                explainReject(turn, "改卡", ex);
                throw ex;
            }
        } finally {
            agentTrace.clear();
        }
    }

    /** 已经拿到会话锁之后改卡片。只改传入的字段。 */
    private ConsultationRes patchCardLocked(ConsultationSession session, CardPatchReq patch) {
        // 急症、转人工、降级：不能改卡。
        SafetyPolicy.rejectIfSafetyStop(session);
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        // 还没生成卡片，不能改。
        if (session.getBusinessStatus() != ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_NOT_READY);
        }
        // 五个可改字段都没传，算空修改。
        if (!hasCardChange(patch)) {
            throw new BusinessException(ErrorCode.EMPTY_CARD_PATCH);
        }
        // 先拷贝当前卡片，下面只覆盖调用方传了的字段。
        ConsultationCardRes current = session.getCard();
        Gender gender = current.gender();
        Integer age = current.age();
        String duration = current.duration();
        String description = current.description();
        List<String> symptoms = current.symptoms();
        boolean descriptionChanged = false;
        if (patch.gender() != null) {
            gender = patch.gender();
        }
        if (patch.age() != null) {
            // 年龄必须是 1～120。
            if (patch.age() < 1 || patch.age() > 120) {
                throw new BusinessException(ErrorCode.INVALID_AGE);
            }
            age = patch.age();
        }
        if (patch.duration() != null) {
            String value = patch.duration().trim();
            // 病程必须带时长，例如「三天」。
            if (!slotParser.validDuration(value)) {
                throw new BusinessException(ErrorCode.INVALID_DURATION);
            }
            duration = value;
        }
        if (patch.description() != null) {
            description = patch.description().trim();
            descriptionChanged = true;
        }
        session.setGender(gender);
        session.setAge(age);
        session.setDuration(duration);
        session.setDescription(description);
        if (descriptionChanged) {
            // 改卡描述也追加进原话，安全评估扫的是全部原话。
            session.appendMessage(null, MessageSource.CARD_EDIT, description);
            assess(session, AuditTrigger.CARD_EDIT);
            // 改完描述命中急症或转人工：卡片作废。
            if (SafetyPolicy.isSafetyStop(session)) {
                SafetyPolicy.applyStopCardPolicy(session);
                return buildResponse(session);
            }
            symptoms = symptomExtractor.extract(description);
        }
        DepartmentRouter.Advice advice = DepartmentRouter.Advice.fromCard(
                current.department(), current.specialty(), current.departmentEvidence());
        DecisionSource source = sourceOf(current);
        boolean patientPicked = source == DecisionSource.PATIENT;
        if (descriptionChanged || (!patientPicked && crossesRouting(current.age(), current.gender(), age, gender))) {
            TriageResult triage = runTriage(session, description, age, gender, MatchDecision.TRIGGER_EDIT_DESCRIPTION);
            if (descriptionChanged) {
                recordSymptoms(session, triage.runId(), symptoms, MatchDecision.TRIGGER_EDIT_DESCRIPTION);
            }
            advice = triage.route().advice();
            source = triage.route().source();
        }
        DepartmentRouter.Advice picked = patientAdvice(patch.department(), patch.specialty(), age);
        if (picked != null) {
            advice = picked;
            source = DecisionSource.PATIENT;
            recordPatientRoute(session, picked);
        }
        ConsultationCardRes card = CardFacts.compose(
                gender, age, duration, description, symptoms, advice, source,
                session.getPatientId(), session.getPatientName());
        session.setCard(card);
        session.setBusinessStatus(ConsultationStatus.CARD_READY);
        return buildResponse(session);
    }

    /** 患者在卡片上改科。没改返回 null。 */
    private DepartmentRouter.Advice patientAdvice(Integer patched, String specialty, Integer age) {
        boolean specialtyChanged = specialty != null && !specialty.isBlank();
        if (patched != null) {
            return registrationAgent.confirm(patched, specialtyChanged ? specialty : null, age);
        }
        if (specialtyChanged) {
            return registrationAgent.confirmSpecialty(specialty, age);
        }
        return null;
    }

    /** 患者改科：归约病名保留给派医生，挂科换成患者选的。 */
    private void recordPatientRoute(ConsultationSession session, DepartmentRouter.Advice picked) {
        RouteDecision route = RegistrationAgent.patientRoute(picked);
        TriageResult previous = session.getTriage();
        String runId = UUID.randomUUID().toString();
        if (previous != null) {
            session.setTriage(previous.withRoute(route));
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("departmentCode", picked.departmentCode());
        output.put("specialty", picked.specialty());
        output.put("previousSpecialty", previous == null ? null : previous.route().advice().specialty());
        record(session, MatchDecision.of(runId, TriageNode.N6_DEPARTMENT, Certainty.CERTAIN, DecisionSource.PATIENT,
                RouteDecision.PATIENT_CONFIRM, output, List.of(), 0), MatchDecision.TRIGGER_PATIENT_DEPARTMENT);
    }

    /** 儿科年龄线和性别科在目录里配置，年龄或性别一变就重跑；规则路径很便宜，模型结果按指纹缓存。 */
    private static boolean crossesRouting(Integer oldAge, Gender oldGender, Integer newAge, Gender newGender) {
        return oldGender != newGender || !java.util.Objects.equals(oldAge, newAge);
    }

    private static DecisionSource sourceOf(ConsultationCardRes card) {
        if (card == null || card.departmentSource() == null) {
            return null;
        }
        try {
            return DecisionSource.valueOf(card.departmentSource());
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    /**
     * 已经拿到会话锁之后确认卡片。
     */
    private ConsultationRes confirmLocked(ConsultationSession session) {
        // 还有没评估完的原话，先补做一次安全检查。
        if (session.getLastEvaluatedSeq() < session.getLastMessageSeq()) {
            assess(session, AuditTrigger.CONFIRM_RECHECK);
        }
        SafetyPolicy.rejectIfSafetyStop(session);
        rejectIfInquiryClosed();
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getBusinessStatus() != ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_NOT_READY);
        }
        requireLiveCard(session.getCard());
        session.setBusinessStatus(ConsultationStatus.CONFIRMED);
        recordConfirm(session);
        return buildResponse(session);
    }

    /** N7：患者确认时是否改过科室或描述。 */
    private void recordConfirm(ConsultationSession session) {
        boolean departmentPatched = sourceOf(session.getCard()) == DecisionSource.PATIENT;
        boolean descriptionPatched = session.messages().stream()
                .anyMatch(message -> message.source() == MessageSource.CARD_EDIT);
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("departmentPatched", departmentPatched);
        output.put("descriptionPatched", descriptionPatched);
        output.put("specialty", session.getCard() == null ? null : session.getCard().specialty());
        boolean patched = departmentPatched || descriptionPatched;
        String runId = session.getTriage() == null ? UUID.randomUUID().toString() : session.getTriage().runId();
        record(session, MatchDecision.of(runId, TriageNode.N7_CARD, Certainty.CERTAIN,
                patched ? DecisionSource.PATIENT : DecisionSource.RULE, patched ? "PATCHED" : "AS_IS",
                output, List.of(), 0), MatchDecision.TRIGGER_CONFIRM);
    }

    /** 已经拿到会话锁之后写入就诊人。 */
    private ConsultationRes bindLocked(ConsultationSession session, PatientRecord patient) {
        bindTrace(session);
        try (AgentSpan turn = beginTurn()) {
            try {
                String input = patient.id();
                ConsultationRes view = attachPatientLocked(session, patient);
                if (view.missingSlot() != null) {
                    return explainTurn(turn, "BIND_PATIENT", "ASK_" + view.missingSlot().name(), input, view);
                }
                return explainTurn(turn, "BIND_PATIENT", "SLOTS_COMPLETE", input, view);
            } catch (BusinessException ex) {
                explainReject(turn, patient.id(), ex);
                throw ex;
            }
        } finally {
            agentTrace.clear();
        }
    }

    private ConsultationRes attachPatientLocked(ConsultationSession session, PatientRecord patient) {
        SafetyPolicy.rejectIfSafetyStop(session);
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        session.setPatientId(patient.id());
        session.setPatientName(patient.name());
        session.setGender(patient.gender());
        session.setAge(patient.age());
        if (session.getBusinessStatus() == ConsultationStatus.CARD_READY && session.getCard() != null) {
            ConsultationCardRes current = session.getCard();
            DepartmentRouter.Advice advice = DepartmentRouter.Advice.fromCard(
                    current.department(), current.specialty(), current.departmentEvidence());
            DecisionSource source = sourceOf(current);
            if (source != DecisionSource.PATIENT
                    && crossesRouting(current.age(), current.gender(), session.getAge(), session.getGender())) {
                TriageResult triage = runTriage(session, current.description(), session.getAge(), session.getGender(),
                        MatchDecision.TRIGGER_BIND_PATIENT);
                advice = triage.route().advice();
                source = triage.route().source();
            }
            session.setCard(CardFacts.compose(
                    session.getGender(),
                    session.getAge(),
                    current.duration(),
                    current.description(),
                    current.symptoms(),
                    advice,
                    source,
                    session.getPatientId(),
                    session.getPatientName()));
            return buildResponse(session);
        }
        return nextView(session, MatchDecision.TRIGGER_BIND_PATIENT);
    }

    /**
     * 对当前全部原话做安全评估，并写一条不含原文的审计。
     */
    private SafetyAssessment assess(ConsultationSession session, AuditTrigger trigger) {
        // 评估前的对外状态、业务状态，写进审计方便对照。
        int previousStatus = SafetyPolicy.view(session, catalog()).status().getCode();
        int businessBefore = session.getBusinessStatus().getCode();
        SafetyAssessment assessment;
        EvaluationKind kind;
        if (session.isUrgentLatched()) {
            // 已经急症：不再跑引擎，也不因「好一点了」解除。
            assessment = SafetyAssessment.none(ModelStatus.SKIPPED);
            kind = EvaluationKind.PINNED_URGENT;
        } else {
            // 用原话 + 已知年龄性别评估。结果只升不降。
            assessment = assessSafety(session.messages(), new Demographics(session.getAge(), session.getGender()));
            SafetyPolicy.merge(session, assessment);
            session.setLastEvaluatedSeq(session.getLastMessageSeq());
            kind = EvaluationKind.EVALUATED;
        }
        ConsultationRes view = SafetyPolicy.view(session, catalog());
        String decisionId = UUID.randomUUID().toString();
        if (!recordingMemo()) {
            bindSafetyTrace(session, decisionId, assessment);
        }
        // 审计只记规则编号和证据位置，不存病情原文。
        session.addPendingAudit(new SafetyDecisionAudit(
                decisionId,
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
                Instant.now(),
                assessment.ruleCertain(),
                assessment.modelTriggerIds()));
        count("status." + view.status().getCode());
        // DRAFT 规则命中只进影子计数，不影响患者决策。
        if (!assessment.shadowRuleIds().isEmpty()) {
            count("shadow.hit");
        }
        return assessment;
    }

    /** 安全评估。锁内遇到预演算过的同一输入直接复用，不再调模型。 */
    private SafetyAssessment assessSafety(List<ConsultationMessage> transcript, Demographics demographics) {
        ModelMemo memo = MEMO.get();
        if (memo != null && !memo.recording()) {
            SafetyAssessment known = memo.safety(transcript, demographics);
            if (known != null) {
                return known;
            }
        }
        SafetyAssessment assessment = safetyGate.assess(transcript, demographics);
        if (memo != null && memo.recording()) {
            memo.putSafety(transcript, demographics, assessment);
        }
        return assessment;
    }

    /** 分诊。和安全评估一样，预演算过的同一输入锁内直接复用。 */
    private TriageRun triageOnce(TriageInput input) {
        ModelMemo memo = MEMO.get();
        if (memo != null && !memo.recording()) {
            TriageRun known = memo.triage(input);
            if (known != null) {
                return known;
            }
        }
        TriageRun run = registrationAgent.triage(input);
        if (memo != null && memo.recording()) {
            memo.putTriage(input, run);
        }
        return run;
    }

    /** 预演那一遍不计数，避免监测翻倍。 */
    private void count(String name) {
        if (!recordingMemo()) {
            metrics.increment(name);
        }
    }

    private boolean recordingMemo() {
        ModelMemo memo = MEMO.get();
        return memo != null && memo.recording();
    }

    /** 按对外状态选出患者提示模板编号。 */
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

    /** 空着的槽位才从这句话里抽。已经有的不覆盖。 */
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

    /** 卡片上的展示描述。多句用换行拼起来，不是原话记录。 */
    private void addDescription(ConsultationSession session, String sentence) {
        if (session.getDescription().isEmpty()) {
            session.setDescription(sentence);
            return;
        }
        session.setDescription(session.getDescription() + "\n" + sentence);
    }

    /**
     * 还在待描述、这句话又太含糊：只追问一次。第二次即使仍含糊也进入补槽，避免死循环。
     */
    private boolean shouldAskForDetail(
            ConsultationSession session,
            String sentence,
            SafetyAssessment safety) {
        if (session.getBusinessStatus() != ConsultationStatus.NEED_DESCRIPTION) {
            return false;
        }
        boolean ask;
        String reason;
        if (!safety.matchedRuleIds().isEmpty() || !safety.shadowRuleIds().isEmpty()) {
            ask = false;
            reason = "SAFETY_SIGNAL";
        } else if (!complaintClarity.isVague(sentence)) {
            ask = false;
            reason = "CLEAR";
        } else {
            int patientTurns = 0;
            for (var message : session.messages()) {
                if (message.source() == MessageSource.MESSAGE) {
                    patientTurns++;
                }
            }
            ask = patientTurns <= 1;
            reason = ask ? "VAGUE_ASK" : "VAGUE_PASS_SECOND";
        }
        record(session, MatchDecision.of(UUID.randomUUID().toString(), TriageNode.N2_VAGUE, Certainty.CERTAIN,
                DecisionSource.RULE, reason, Map.of("ask", ask), List.of(), 0),
                ask ? MatchDecision.TRIGGER_VAGUE : MatchDecision.TRIGGER_CARD);
        return ask;
    }

    /** 缺槽位就追问，齐了就生成卡片。 */
    private ConsultationRes nextView(ConsultationSession session, int trigger) {
        MissingSlot missing = Slots.nextMissing(session);
        recordSlot(session, missing, trigger);
        if (missing != null) {
            session.setBusinessStatus(ConsultationStatus.NEED_SLOT);
            session.setCard(null);
            return buildResponse(session);
        }
        return refreshCard(session, trigger);
    }

    /**
     * 槽位已齐时出卡或补述刷新卡。程序能定科室就定；定不了才由 TriageEngine 调主诉模型。
     */
    private ConsultationRes refreshCard(ConsultationSession session, int trigger) {
        List<String> symptoms = symptomExtractor.extract(session.getDescription());
        TriageResult triage = runTriage(session, session.getDescription(), session.getAge(), session.getGender(), trigger);
        recordSymptoms(session, triage.runId(), symptoms, trigger);
        session.setCard(CardFacts.compose(
                session.getGender(),
                session.getAge(),
                session.getDuration(),
                session.getDescription(),
                symptoms,
                triage.route().advice(),
                triage.route().source(),
                session.getPatientId(),
                session.getPatientName()));
        session.setBusinessStatus(ConsultationStatus.CARD_READY);
        return buildResponse(session);
    }

    /** 预演那一遍不写 agent.turn，避免同一轮 HTTP 出现两条决策 span。 */
    private AgentSpan beginTurn() {
        if (recordingMemo()) {
            return AgentTrace.NOOP.span(AgentTraceNames.TURN);
        }
        return agentTrace.span(AgentTraceNames.TURN);
    }

    private ConsultationRes explainTurn(
            AgentSpan turn, String node, String reason, String input, ConsultationRes view) {
        AgentTraceZh.turn(turn, TurnSnapshot.from(node, reason, input, view));
        return view;
    }

    private void explainReject(AgentSpan turn, String input, BusinessException ex) {
        String code = ex.getErrorCode() == null ? "ERROR" : ex.getErrorCode().name();
        AgentTraceZh.turn(turn, TurnSnapshot.reject(input, code, ex.getMessage()));
    }

    private static String patchFields(CardPatchReq patch) {
        if (patch == null) {
            return "EMPTY";
        }
        StringBuilder fields = new StringBuilder();
        appendField(fields, "gender", patch.gender());
        appendField(fields, "age", patch.age());
        appendField(fields, "duration", patch.duration());
        appendField(fields, "description", patch.description());
        appendField(fields, "department", patch.department());
        appendField(fields, "specialty", patch.specialty());
        return fields.isEmpty() ? "EMPTY" : fields.toString();
    }

    private static void appendField(StringBuilder fields, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (!fields.isEmpty()) {
            fields.append(',');
        }
        fields.append(name);
    }

    /** 把咨询编号和账号打到当前 HTTP 根 span。 */
    private void bindTrace(ConsultationSession session) {
        agentTrace.bind(new AgentTraceContext(
                session.getId(),
                null,
                session.getLastMessageSeq(),
                null,
                catalog().catalogVersion(),
                null,
                session.getAccountId()));
    }

    /** 跑一次 N4+N6，结论存进会话，决策排队落库。 */
    private TriageResult runTriage(ConsultationSession session, String description, Integer age, Gender gender, int trigger) {
        bindTrace(session);
        try {
            TriageRun run = triageOnce(new TriageInput(description, age, gender));
            session.setTriage(run.result());
            session.addPendingDecisions(run.decisions(), null, trigger);
            return run.result();
        } finally {
            agentTrace.clear();
        }
    }

    private void bindSafetyTrace(ConsultationSession session, String decisionId, SafetyAssessment assessment) {
        agentTrace.bind(new AgentTraceContext(
                session.getId(),
                null,
                session.getLastMessageSeq(),
                decisionId,
                catalog().catalogVersion(),
                RuleSafetyEngine.VERSION,
                session.getAccountId()));
        try (var ignored = agentTrace.span("agent.safety.decision")) {
            ignored.attr("model_status", assessment.modelStatus().name())
                    .attr("matched_rule_ids", String.join(",", assessment.matchedRuleIds()))
                    .attr("shadow_rule_ids", String.join(",", assessment.shadowRuleIds()))
                    .attr("model_called", assessment.modelStatus() != ModelStatus.SKIPPED
                            && assessment.modelStatus() != ModelStatus.DISABLED)
                    .attr("rule_certain", assessment.ruleCertain());
        } finally {
            agentTrace.clear();
        }
    }

    /** N3：槽位是否齐。 */
    private void recordSlot(ConsultationSession session, MissingSlot missing, int trigger) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("missing", missing == null ? null : missing.name());
        output.put("hasGender", session.getGender() != null);
        output.put("hasAge", session.getAge() != null);
        output.put("hasDuration", session.getDuration() != null);
        record(session, MatchDecision.of(UUID.randomUUID().toString(), TriageNode.N3_SLOT, Certainty.CERTAIN,
                DecisionSource.RULE, missing == null ? "SLOTS_COMPLETE" : "ASK_" + missing.name(), output, List.of(), 0), trigger);
    }

    /** N5：卡片上的症状标签。 */
    private void recordSymptoms(ConsultationSession session, String runId, List<String> symptoms, int trigger) {
        record(session, MatchDecision.of(runId, TriageNode.N5_SYMPTOM, Certainty.CERTAIN, DecisionSource.RULE,
                symptoms.isEmpty() ? "NO_SYMPTOM" : "SYMPTOM_HIT", Map.of("symptoms", symptoms), List.of(), 0), trigger);
    }

    void record(ConsultationSession session, MatchDecision decision, int trigger) {
        record(session, decision, null, trigger);
    }

    void record(ConsultationSession session, MatchDecision decision, String orderId, int trigger) {
        String catalogVersion = session.getTriage() == null ? null : session.getTriage().catalogVersion();
        String fingerprint = session.getTriage() == null ? null : session.getTriage().fingerprint();
        session.addPendingDecisions(
                List.of(decision.withVersion(catalogVersion, fingerprint, decision.shadowIds())), orderId, trigger);
    }

    /** 空文本：请重新描述；如果正在补槽，状态仍是待补槽。 */
    private ConsultationRes blankView(ConsultationSession session) {
        if (session.getBusinessStatus() != ConsultationStatus.NEED_SLOT) {
            session.setBusinessStatus(ConsultationStatus.NEED_DESCRIPTION);
        }
        return buildResponse(session);
    }

    /** 按安全状态和业务状态拼出给前端的画面。出卡后即可选档。 */
    ConsultationRes buildResponse(ConsultationSession session) {
        ConsultationRes view = SafetyPolicy.view(session, catalog());
        count("view." + view.status().getCode());
        List<PayPackageRes> packages = view.status() == ConsultationStatus.CONFIRMED
                || view.status() == ConsultationStatus.CARD_READY ? PayCatalog.list() : null;
        ConsultOrder order = session.getOrder();
        ConsultationCardRes card = markStaleIfStopped(view.card(), order);
        String prompt = card != null && card.stale()
                ? "该科室已停诊，请在卡片上改科。"
                : view.prompt();
        return new ConsultationRes(
                view.consultationId(),
                view.status(),
                prompt,
                view.missingSlot(),
                card,
                view.safetyNotice(),
                view.handoff(),
                packages,
                order == null ? null : order.toRes());
    }

    void requireLiveCard(ConsultationCardRes card) {
        if (card == null || card.department() == null || card.specialty() == null) {
            throw new BusinessException(ErrorCode.INVALID_DEPARTMENT);
        }
        registrationAgent.snapshot(card.age()).requireLiveSpecialty(card.department(), card.specialty());
    }

    private ConsultationCardRes markStaleIfStopped(ConsultationCardRes card, ConsultOrder order) {
        if (card == null || order != null) {
            return card;
        }
        if (registrationAgent.snapshot(card.age()).isLiveSpecialty(card.department(), card.specialty())) {
            return card;
        }
        return card.withStale(true);
    }

    /** 可改字段都没传，就算空修改。 */
    private boolean hasCardChange(CardPatchReq patch) {
        return patch.gender() != null
                || patch.age() != null
                || patch.duration() != null
                || patch.description() != null
                || patch.department() != null
                || (patch.specialty() != null && !patch.specialty().isBlank());
    }

    /** 问诊关闭时确认和下单都挡住。 */
    void rejectIfInquiryClosed() {
        if (inquiry.isClosed()) {
            throw new BusinessException(ErrorCode.INQUIRY_CLOSED);
        }
    }

    private static String requireAccount(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(ErrorCode.LOGIN_REQUIRED);
        }
        return accountId.trim();
    }

    private static void bindOwner(ConsultationSession session, String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return;
        }
        String owner = accountId.trim();
        if (session.getAccountId() == null || session.getAccountId().isBlank()) {
            session.setAccountId(owner);
            return;
        }
        if (!owner.equals(session.getAccountId())) {
            throw new BusinessException(ErrorCode.CONSULT_NOT_OWNED);
        }
    }

    private static void assertReadable(ConsultationSession session, String accountId) {
        if (session == null || session.getAccountId() == null || !accountId.equals(session.getAccountId())) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
    }

    private static String clipTitle(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) {
            return "新的咨询";
        }
        return value.length() > 18 ? value.substring(0, 18) : value;
    }

    /** 当前生效的急症目录，用来取提示文案。 */
    private SafetyCatalog catalog() {
        return safetyGate.catalog();
    }
}
