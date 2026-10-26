package com.moonligh.consult.agent.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.enums.ServiceType;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.api.res.PayPackageRes;
import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.CardFacts;
import com.moonligh.consult.agent.domain.ComplaintClarity;
import com.moonligh.consult.agent.domain.ConsultMinutes;
import com.moonligh.consult.agent.domain.ConsultOrder;
import com.moonligh.consult.agent.domain.ConsultationRepository;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.RegistrationAgent;
import com.moonligh.consult.agent.domain.PayCatalog;
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
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 咨询编排：先存原话、先做安全评估，再走挂号业务。
 */
@Service
public class ConsultationService {

    /** 会话存取。同一咨询编号会排队，避免两条消息互相覆盖。 */
    private final ConsultationRepository repository;
    /** 安全闸门：先规则，必要时再让模型补漏。 */
    private final SafetyGate safetyGate;
    /** 从一句话里抽性别、年龄、病程。 */
    private final SlotParser slotParser;
    /** 从病情描述里抽症状标签。 */
    private final SymptomExtractor symptomExtractor;
    /** 工具型挂号 Agent：纠错+关键词快路径，对不上才允许模型补科室。 */
    private final RegistrationAgent registrationAgent;
    /** 安全状态计数，给监测用，不含病情原文。 */
    private final SafetyMetrics metrics;
    /** 判断病情描述是否太含糊。 */
    private final ComplaintClarity complaintClarity;
    /** 问诊通道关闭时不能确认、不能下单。 */
    private final InquiryProperties inquiry;
    /** 账号下的就诊人档案。绑到本轮咨询时写入性别、年龄和姓名。 */
    private final PatientStore patients;
    /** 支付后按二级科和擅长派医生。测试可不接。 */
    private final DoctorAssigner doctorAssigner;

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            DepartmentRouter departmentRouter,
            SafetyMetrics metrics) {
        this(repository, safetyGate, slotParser, symptomExtractor, new RegistrationAgent(departmentRouter), metrics, new InquiryProperties(), new MemoryPatientStore());
    }

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            DepartmentRouter departmentRouter,
            SafetyMetrics metrics,
            InquiryProperties inquiry) {
        this(repository, safetyGate, slotParser, symptomExtractor, new RegistrationAgent(departmentRouter), metrics, inquiry, new MemoryPatientStore());
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
        this(repository, safetyGate, slotParser, symptomExtractor, new RegistrationAgent(departmentRouter), metrics, inquiry, patients);
    }

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            RegistrationAgent registrationAgent,
            SafetyMetrics metrics,
            InquiryProperties inquiry) {
        this(repository, safetyGate, slotParser, symptomExtractor, registrationAgent, metrics, inquiry, new MemoryPatientStore());
    }

    public ConsultationService(
            ConsultationRepository repository,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            RegistrationAgent registrationAgent,
            SafetyMetrics metrics,
            InquiryProperties inquiry,
            PatientStore patients) {
        this(repository, safetyGate, slotParser, symptomExtractor, registrationAgent, metrics, inquiry, patients, null);
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
    }

    /** 简写入口：不传 messageId、不传服务类型。 */
    public ConsultationRes acceptMessage(String consultationId, String text) {
        return acceptMessage(consultationId, null, null, text);
    }

    /**
     * 收一句病情。先加锁取会话，再评估，最后保存。
     */
    public ConsultationRes acceptMessage(String consultationId, String messageId, Integer serviceTypeCode, String text) {
        // 幂等键最长 64 个字符，超了直接参数错误。
        if (messageId != null && messageId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAM_INVALID);
        }
        // 把 1、2、3 转成服务类型；非法数字会变成 1114。
        ServiceType requestedType = parseServiceType(serviceTypeCode);
        // 同一咨询排队执行；锁内办事，办完再保存。
        return repository.runLocked(consultationId, session -> acceptLocked(session, messageId, requestedType, text));
    }

    /**
     * 修改已生成的卡片。急症、转人工、安全降级时不能改。
     */
    public ConsultationRes updateCard(CardPatchReq patch) {
        if (patch == null) {
            throw new BusinessException(ErrorCode.EMPTY_CARD_PATCH);
        }
        return repository.runLocked(patch.consultationId(), session -> updateLocked(session, patch));
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

    public ConsultationRes placeOrder(String consultationId, Integer packageCode, Integer consultMinutes) {
        return repository.runLocked(consultationId, session -> placeOrderLocked(session, packageCode, consultMinutes));
    }

    /** 患者结束问诊，释放医生占坑。 */
    public ConsultationRes endConsult(String consultationId, String reason) {
        return repository.runLocked(consultationId, session -> endConsultLocked(session, reason));
    }

    /** 问诊中改时长，从现在起重新计时。 */
    public ConsultationRes setConsultMinutes(String consultationId, Integer consultMinutes) {
        return repository.runLocked(consultationId, session -> setConsultMinutesLocked(session, consultMinutes));
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
        return repository.runLocked(consultationId, session -> bindLocked(session, patient));
    }

    /**
     * 已经拿到会话锁之后收消息。
     */
    private ConsultationRes acceptLocked(
            ConsultationSession session,
            String messageId,
            ServiceType requestedType,
            String text) {
        // 同一个 messageId 再来一次：不重复入库，直接返回当前画面（含档位和订单）。
        if (messageId != null && session.hasMessage(messageId)) {
            return buildResponse(session);
        }
        // 去掉首尾空格。全是空格就当成没说话。
        String sentence = text == null ? "" : text.trim();
        if (sentence.isEmpty()) {
            // 已经急症/转人工/降级时，空句也只回安全画面。
            if (SafetyPolicy.isSafetyStop(session)) {
                return SafetyPolicy.view(session, catalog());
            }
            return blankView(session);
        }
        // 原话只追加，不覆盖以前说过的话。
        session.appendMessage(messageId, MessageSource.MESSAGE, sentence);
        // 用全部原话做安全评估；已急症则钉死。
        assess(session, AuditTrigger.MESSAGE);
        // 确认后再改不同服务类型，这里记一笔拒绝。
        boolean serviceTypeRejected = applyServiceType(session, requestedType);
        // 本轮升到急症、转人工、降级：先回安全画面，不抛 1104/1105。
        if (SafetyPolicy.isSafetyStop(session)) {
            SafetyPolicy.applyStopCardPolicy(session);
            return buildResponse(session);
        }
        // 卡片已确认，还想改服务类型：原话已保存，这里抛 1113。
        if (serviceTypeRejected) {
            throw new BusinessException(ErrorCode.SERVICE_TYPE_LOCKED);
        }
        // 已确认且没有安全升级：普通消息返回 1105。原话已经入库。
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        // 卡片待确认：允许继续补主诉，拼进描述后重跑分诊；改科仍走改卡接口。
        if (session.getBusinessStatus() == ConsultationStatus.CARD_READY) {
            fillEmptySlots(session, sentence);
            addDescription(session, sentence);
            return refreshCard(session, MatchDecision.TRIGGER_EDIT_DESCRIPTION);
        }
        // 第一句只说「有点不舒服」：先追问具体病情，不进入选就诊人。
        if (shouldAskForDetail(session, sentence)) {
            session.setBusinessStatus(ConsultationStatus.NEED_DESCRIPTION);
            session.setCard(null);
            return buildResponse(session);
        }
        // 空着的槽位才从这句里抽，已经有的不覆盖。
        fillEmptySlots(session, sentence);
        // 卡片展示用的描述另存一份，和原话分开。
        addDescription(session, sentence);
        // 缺槽位就追问；齐了就出卡片。
        return nextView(session, MatchDecision.TRIGGER_CARD);
    }

    /**
     * 已经拿到会话锁之后改卡片。只改传入的字段。
     */
    private ConsultationRes updateLocked(ConsultationSession session, CardPatchReq patch) {
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
        DepartmentRouter.Advice advice = new DepartmentRouter.Advice(
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
        DepartmentRouter.Advice picked = patientAdvice(patch.department(), patch.specialty());
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
    private DepartmentRouter.Advice patientAdvice(Department patched, String specialty) {
        boolean specialtyChanged = specialty != null && !specialty.isBlank();
        if (patched != null) {
            return registrationAgent.confirm(patched, specialtyChanged ? specialty : null);
        }
        if (specialtyChanged) {
            return registrationAgent.confirmSpecialty(specialty);
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
        output.put("departmentCode", picked.department().getCode());
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

    /**
     * 已经拿到会话锁之后下单。急症和未确认都不能买。
     */
    private ConsultationRes placeOrderLocked(ConsultationSession session, Integer packageCode, Integer consultMinutes) {
        SafetyPolicy.rejectIfSafetyStop(session);
        rejectIfInquiryClosed();
        if (session.getBusinessStatus() != ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
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
                return buildResponse(session);
            }
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
        int minutes = ConsultMinutes.of(consultMinutes);
        ConsultOrder order = ConsultOrder.paidOf(pkg, minutes);
        if (doctorAssigner != null) {
            List<String> terms = canonicalTerms(session);
            AssignedDoctorRes doctor = doctorAssigner.assign(
                    session.getId(), order.orderId(), session.getCard(), terms, minutes);
            order = order.withDoctor(doctor);
            recordAssign(session, order.orderId(), doctor, terms);
        }
        session.setOrder(order);
        return buildResponse(session);
    }

    private ConsultationRes endConsultLocked(ConsultationSession session, String reason) {
        ConsultOrder order = session.getOrder();
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
        if (order.endedAt() != null) {
            return buildResponse(session);
        }
        String code = reason == null || reason.isBlank() ? "ENDED" : reason.trim();
        // 页面倒计时和 XXL 都可能走到这里，过期一律记 TIMEOUT
        if (order.endsAt() != null && Instant.now().isAfter(order.endsAt())) {
            code = "TIMEOUT";
        }
        if (doctorAssigner != null) {
            doctorAssigner.release(order.orderId(), code);
        }
        session.setOrder(order.ended(Instant.now()));
        recordRelease(session, code);
        return buildResponse(session);
    }

    private ConsultationRes setConsultMinutesLocked(ConsultationSession session, Integer consultMinutes) {
        ConsultOrder order = session.getOrder();
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
        if (order.endedAt() != null) {
            throw new BusinessException(ErrorCode.CONSULT_ENDED);
        }
        int minutes = ConsultMinutes.of(consultMinutes);
        if (doctorAssigner != null) {
            doctorAssigner.extend(order.orderId(), minutes);
        }
        session.setOrder(order.withMinutes(minutes));
        return buildResponse(session);
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
        return registrationAgent.engine().canon().rule(card.description()).termNames();
    }

    /** N8 派医生 + N9 占坑。 */
    private void recordAssign(ConsultationSession session, String orderId, AssignedDoctorRes doctor, List<String> terms) {
        String runId = session.getTriage() == null ? UUID.randomUUID().toString() : session.getTriage().runId();
        String matchSource = doctor == null || doctor.matchSource() == null ? "UNKNOWN" : doctor.matchSource();
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("doctorId", doctor == null ? null : doctor.id());
        output.put("specialty", doctor == null ? null : doctor.specialty());
        output.put("canonicalTerms", terms);
        output.put("skillHit", doctor == null ? null : doctor.evidence());
        boolean skill = matchSource.startsWith("SKILL_");
        record(session, MatchDecision.of(runId, TriageNode.N8_DOCTOR, skill ? Certainty.CERTAIN : Certainty.UNCERTAIN,
                skill ? DecisionSource.RULE : DecisionSource.FALLBACK, matchSource, output, List.of(), 0),
                orderId, MatchDecision.TRIGGER_ASSIGN);
        record(session, MatchDecision.of(runId, TriageNode.N9_OCCUPY, Certainty.CERTAIN, DecisionSource.RULE,
                doctor == null ? "NO_DOCTOR" : "OCCUPIED",
                Map.of("scheduleId", doctor == null || doctor.scheduleId() == null ? "" : doctor.scheduleId()),
                List.of(), 0), orderId, MatchDecision.TRIGGER_ASSIGN);
    }

    private void recordRelease(ConsultationSession session, String reason) {
        ConsultOrder order = session.getOrder();
        String runId = session.getTriage() == null ? UUID.randomUUID().toString() : session.getTriage().runId();
        record(session, MatchDecision.of(runId, TriageNode.N9_OCCUPY, Certainty.CERTAIN, DecisionSource.RULE,
                reason, Map.of("orderId", order == null ? "" : order.orderId()), List.of(), 0),
                order == null ? null : order.orderId(), MatchDecision.TRIGGER_ASSIGN);
    }

    /** 已经拿到会话锁之后写入就诊人。 */
    private ConsultationRes bindLocked(ConsultationSession session, PatientRecord patient) {
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
            DepartmentRouter.Advice advice = new DepartmentRouter.Advice(
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
    private void assess(ConsultationSession session, AuditTrigger trigger) {
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
            assessment = safetyGate.assess(session.messages(), new Demographics(session.getAge(), session.getGender()));
            SafetyPolicy.merge(session, assessment);
            session.setLastEvaluatedSeq(session.getLastMessageSeq());
            kind = EvaluationKind.EVALUATED;
        }
        ConsultationRes view = SafetyPolicy.view(session, catalog());
        // 审计只记规则编号和证据位置，不存病情原文。
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
                Instant.now(),
                assessment.ruleCertain(),
                assessment.modelTriggerIds()));
        metrics.increment("status." + view.status().getCode());
        // DRAFT 规则命中只进影子计数，不影响患者决策。
        if (!assessment.shadowRuleIds().isEmpty()) {
            metrics.increment("shadow.hit");
        }
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

    /** 写入服务类型。确认后改成不同值返回 true，调用方据此抛 1113。 */
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

    /** 把前端传来的 1/2/3 转成枚举。对不上就是 1114。 */
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
    private boolean shouldAskForDetail(ConsultationSession session, String sentence) {
        if (session.getBusinessStatus() != ConsultationStatus.NEED_DESCRIPTION) {
            return false;
        }
        boolean ask;
        String reason;
        if (!complaintClarity.isVague(sentence)) {
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

    /** 跑一次 N4+N6，结论存进会话，决策排队落库。 */
    private TriageResult runTriage(ConsultationSession session, String description, Integer age, Gender gender, int trigger) {
        TriageRun run = registrationAgent.triage(new TriageInput(description, age, gender));
        session.setTriage(run.result());
        session.addPendingDecisions(run.decisions(), null, trigger);
        return run.result();
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

    private void record(ConsultationSession session, MatchDecision decision, int trigger) {
        record(session, decision, null, trigger);
    }

    private void record(ConsultationSession session, MatchDecision decision, String orderId, int trigger) {
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

    /** 按安全状态和业务状态拼出给前端的画面。已确认时带上四档和下单结果。 */
    private ConsultationRes buildResponse(ConsultationSession session) {
        ConsultationRes view = SafetyPolicy.view(session, catalog());
        metrics.increment("view." + view.status().getCode());
        List<PayPackageRes> packages = view.status() == ConsultationStatus.CONFIRMED ? PayCatalog.list() : null;
        ConsultOrder order = session.getOrder();
        return new ConsultationRes(
                view.consultationId(),
                view.status(),
                view.prompt(),
                view.missingSlot(),
                view.card(),
                view.safetyNotice(),
                view.handoff(),
                packages,
                order == null ? null : order.toRes());
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
    private void rejectIfInquiryClosed() {
        if (inquiry.isClosed()) {
            throw new BusinessException(ErrorCode.INQUIRY_CLOSED);
        }
    }

    /** 当前生效的急症目录，用来取提示文案。 */
    private SafetyCatalog catalog() {
        return safetyGate.catalog();
    }
}
