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
import com.moonligh.consult.agent.api.res.PayPackageRes;
import com.moonligh.consult.agent.domain.ConsultOrder;
import com.moonligh.consult.agent.domain.ConsultationRepository;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.DepartmentRouter;
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
    /** 按关键词推荐科室。 */
    private final DepartmentRouter departmentRouter;
    /** 安全状态计数，给监测用，不含病情原文。 */
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
        return repository.runLocked(consultationId, session -> placeOrderLocked(session, packageCode));
    }

    /**
     * 已经拿到会话锁之后收消息。
     */
    private ConsultationRes acceptLocked(
            ConsultationSession session,
            String messageId,
            ServiceType requestedType,
            String text) {
        // 同一个 messageId 再来一次：不重复入库，直接返回当前画面。
        if (messageId != null && session.hasMessage(messageId)) {
            return SafetyPolicy.view(session, catalog());
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
        // 卡片待确认：请走改卡接口，不要用发消息改卡片。
        if (session.getBusinessStatus() == ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_PENDING_CONFIRMATION);
        }
        // 空着的槽位才从这句里抽，已经有的不覆盖。
        fillEmptySlots(session, sentence);
        // 卡片展示用的描述另存一份，和原话分开。
        addDescription(session, sentence);
        // 缺槽位就追问；齐了就出卡片。
        return nextView(session);
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
        Department department = current.department();
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
        if (patch.department() != null) {
            department = patch.department();
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
            // 调用方没指定科室时，按新描述重新推荐。
            if (patch.department() == null) {
                department = departmentRouter.recommend(description);
            }
        }
        ConsultationCardRes card = new ConsultationCardRes(gender, age, duration, description, symptoms, department);
        session.setCard(card);
        session.setBusinessStatus(ConsultationStatus.CARD_READY);
        return buildResponse(session);
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
        if (session.getBusinessStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getBusinessStatus() != ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_NOT_READY);
        }
        session.setBusinessStatus(ConsultationStatus.CONFIRMED);
        return buildResponse(session);
    }

    /**
     * 已经拿到会话锁之后下单。急症和未确认都不能买。
     */
    private ConsultationRes placeOrderLocked(ConsultationSession session, Integer packageCode) {
        SafetyPolicy.rejectIfSafetyStop(session);
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
        session.setOrder(ConsultOrder.paidOf(pkg));
        return buildResponse(session);
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
                Instant.now()));
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

    /** 缺槽位就追问，齐了就生成卡片。 */
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

    /** 五个可改字段都没传，就算空修改。 */
    private boolean hasCardChange(CardPatchReq patch) {
        return patch.gender() != null
                || patch.age() != null
                || patch.duration() != null
                || patch.description() != null
                || patch.department() != null;
    }

    /** 当前生效的急症目录，用来取提示文案。 */
    private SafetyCatalog catalog() {
        return safetyGate.catalog();
    }
}
