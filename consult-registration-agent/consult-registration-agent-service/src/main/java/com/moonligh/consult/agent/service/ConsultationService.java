package com.moonligh.consult.agent.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.ConsultationSession;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.InMemoryConsultationStore;
import com.moonligh.consult.agent.domain.SafetyGate;
import com.moonligh.consult.agent.domain.SafetyHit;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 挂号咨询编排：收消息、改卡片、确认。
 * 急症分流、补槽、抽症状、荐科室都在 domain 里，这里只按顺序调用。
 * 顺序：先急症和转人工，其余补槽后生成卡片，交给线上医生。
 */
@Service
public class ConsultationService {

    static final String URGENT_PROMPT = "请立即拨打 120，或前往最近的实体医院急诊。";
    static final String HUMAN_PROMPT = "请转人工协助。当前无法自动完成导诊。";
    static final String REDESCRIBE_PROMPT = "请重新描述病情。";

    private final InMemoryConsultationStore store;
    private final SafetyGate safetyGate;
    private final SlotParser slotParser;
    private final SymptomExtractor symptomExtractor;
    private final DepartmentRouter departmentRouter;

    public ConsultationService(
            InMemoryConsultationStore store,
            SafetyGate safetyGate,
            SlotParser slotParser,
            SymptomExtractor symptomExtractor,
            DepartmentRouter departmentRouter) {
        this.store = store;
        this.safetyGate = safetyGate;
        this.slotParser = slotParser;
        this.symptomExtractor = symptomExtractor;
        this.departmentRouter = departmentRouter;
    }

    /** 收一句病情描述。已确认、待确认卡片、安全分流三种状态不再走普通补槽。 */
    public ConsultationRes acceptMessage(String consultationId, String text) {
        ConsultationSession session = findOrCreate(consultationId);
        String sentence = text == null ? "" : text.trim();

        if (session.getStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getStatus() == ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_PENDING_CONFIRMATION);
        }
        if (isSafetyStop(session.getStatus())) {
            return safetyView(session);
        }
        if (sentence.isEmpty()) {
            return blankView(session);
        }

        fillEmptySlots(session, sentence);
        addDescription(session, sentence);

        ConsultationRes gated = applySafety(session, session.getDescription());
        if (gated != null) {
            return gated;
        }
        return nextView(session);
    }

    /** 改卡片。只改传入的字段；改了病情描述要再跑安全分流和科室推荐。 */
    public ConsultationRes updateCard(CardPatchReq patch) {
        if (patch == null) {
            throw new BusinessException(ErrorCode.EMPTY_CARD_PATCH);
        }

        ConsultationSession session = require(patch.consultationId());
        rejectIfSafetyStop(session.getStatus());
        if (session.getStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getStatus() != ConsultationStatus.CARD_READY) {
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
            ConsultationRes gated = applySafety(session, description);
            if (gated != null) {
                return gated;
            }
        }
        if (descriptionChanged) {
            symptoms = symptomExtractor.extract(description);
            if (patch.department() == null) {
                department = departmentRouter.recommend(description);
            } else {
                department = patch.department();
            }
        }

        ConsultationCardRes card = new ConsultationCardRes(gender, age, duration, description, symptoms, department);
        session.setCard(card);
        store.save(session);
        return new ConsultationRes(session.getId(), ConsultationStatus.CARD_READY, null, null, card);
    }

    /** 确认卡片。安全分流或还没生成卡片时不能确认。 */
    public ConsultationRes confirm(String consultationId) {
        ConsultationSession session = require(consultationId);
        rejectIfSafetyStop(session.getStatus());
        if (session.getStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getStatus() != ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_NOT_READY);
        }
        session.setStatus(ConsultationStatus.CONFIRMED);
        store.save(session);
        return new ConsultationRes(session.getId(), ConsultationStatus.CONFIRMED, null, null, session.getCard());
    }

    /** 没带编号就新建一轮；带了编号必须能找到。 */
    private ConsultationSession findOrCreate(String consultationId) {
        if (consultationId == null || consultationId.isBlank()) {
            ConsultationSession created = new ConsultationSession(UUID.randomUUID().toString());
            store.save(created);
            return created;
        }
        return require(consultationId);
    }

    /** 按编号取会话，找不到就抛 1101。 */
    private ConsultationSession require(String consultationId) {
        ConsultationSession session = store.find(consultationId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        return session;
    }

    /** 空着的槽位才从这句话里抽，已经有的不覆盖。 */
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

    /** 多句话用换行拼在一起，后面急症分流看完整描述。 */
    private void addDescription(ConsultationSession session, String sentence) {
        if (session.getDescription().isEmpty()) {
            session.setDescription(sentence);
            return;
        }
        session.setDescription(session.getDescription() + "\n" + sentence);
    }

    /** 还缺槽位就追问；齐了才抽症状、荐科室、生成卡片。 */
    private ConsultationRes nextView(ConsultationSession session) {
        MissingSlot missing = nextMissing(session);
        if (missing != null) {
            session.setStatus(ConsultationStatus.NEED_SLOT);
            session.setCard(null);
            store.save(session);
            return new ConsultationRes(session.getId(), ConsultationStatus.NEED_SLOT, promptFor(missing), missing, null);
        }

        ConsultationCardRes card = new ConsultationCardRes(
                session.getGender(),
                session.getAge(),
                session.getDuration(),
                session.getDescription(),
                symptomExtractor.extract(session.getDescription()),
                departmentRouter.recommend(session.getDescription()));
        session.setCard(card);
        session.setStatus(ConsultationStatus.CARD_READY);
        store.save(session);
        return new ConsultationRes(session.getId(), ConsultationStatus.CARD_READY, null, null, card);
    }

    /** 空文本：请对方重新描述，并指出还缺哪个槽位。 */
    private ConsultationRes blankView(ConsultationSession session) {
        if (session.getStatus() == ConsultationStatus.NEED_SLOT) {
            MissingSlot missing = nextMissing(session);
            return new ConsultationRes(session.getId(), ConsultationStatus.NEED_SLOT, REDESCRIBE_PROMPT, missing, null);
        }
        return new ConsultationRes(
                session.getId(), ConsultationStatus.NEED_DESCRIPTION, REDESCRIBE_PROMPT, null, null);
    }

    /** 命中安全分流则改状态、丢掉卡片；没命中返回 null。 */
    private ConsultationRes applySafety(ConsultationSession session, String description) {
        SafetyHit hit = safetyGate.classify(description);
        if (hit == SafetyHit.NONE) {
            return null;
        }
        session.setStatus(statusOf(hit));
        session.setCard(null);
        store.save(session);
        return safetyView(session);
    }

    /** 急症、线下评估、人工协助：只给提示，不给卡片。 */
    private ConsultationRes safetyView(ConsultationSession session) {
        return new ConsultationRes(session.getId(), session.getStatus(), promptOf(session.getStatus()), null, null);
    }

    private boolean isSafetyStop(ConsultationStatus status) {
        return status == ConsultationStatus.URGENT || status == ConsultationStatus.NEED_HUMAN;
    }

    private void rejectIfSafetyStop(ConsultationStatus status) {
        if (status == ConsultationStatus.URGENT) {
            throw new BusinessException(ErrorCode.URGENT_STOP);
        }
        if (status == ConsultationStatus.NEED_HUMAN) {
            throw new BusinessException(ErrorCode.HUMAN_STOP);
        }
    }

    private ConsultationStatus statusOf(SafetyHit hit) {
        if (hit == SafetyHit.HUMAN) {
            return ConsultationStatus.NEED_HUMAN;
        }
        return ConsultationStatus.URGENT;
    }

    private String promptOf(ConsultationStatus status) {
        if (status == ConsultationStatus.NEED_HUMAN) {
            return HUMAN_PROMPT;
        }
        return URGENT_PROMPT;
    }

    /** 缺什么就追问什么，顺序固定：性别 → 年龄 → 病程。 */
    private MissingSlot nextMissing(ConsultationSession session) {
        if (session.getGender() == null) {
            return MissingSlot.GENDER;
        }
        if (session.getAge() == null) {
            return MissingSlot.AGE;
        }
        if (session.getDuration() == null) {
            return MissingSlot.DURATION;
        }
        return null;
    }

    /** 追问文案。 */
    private String promptFor(MissingSlot slot) {
        if (slot == MissingSlot.GENDER) {
            return "请补充性别。";
        }
        if (slot == MissingSlot.AGE) {
            return "请补充年龄。";
        }
        return "请补充病程。";
    }

    /** 五个可改字段都没传，就算空修改。 */
    private boolean hasCardChange(CardPatchReq patch) {
        return patch.gender() != null
                || patch.age() != null
                || patch.duration() != null
                || patch.description() != null
                || patch.department() != null;
    }
}
