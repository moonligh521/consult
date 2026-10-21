package com.moonligh.consult.agent;

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
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

@Service
public class ConsultationService {

    static final String URGENT_PROMPT = "请立即拨打 120，或前往最近的实体医院急诊。";
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

    public ConsultationRes acceptMessage(String consultationId, String text) {
        ConsultationSession session = findOrCreate(consultationId);
        String sentence = text == null ? "" : text.trim();

        if (session.getStatus() == ConsultationStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.ALREADY_CONFIRMED);
        }
        if (session.getStatus() == ConsultationStatus.CARD_READY) {
            throw new BusinessException(ErrorCode.CARD_PENDING_CONFIRMATION);
        }
        if (session.getStatus() == ConsultationStatus.URGENT) {
            return urgentView(session);
        }
        if (sentence.isEmpty()) {
            return blankView(session);
        }

        fillEmptySlots(session, sentence);
        addDescription(session, sentence);

        if (safetyGate.triggered(session.getDescription())) {
            session.setStatus(ConsultationStatus.URGENT);
            session.setCard(null);
            store.save(session);
            return urgentView(session);
        }
        return nextView(session);
    }

    public ConsultationRes updateCard(CardPatchReq patch) {
        if (patch == null) {
            throw new BusinessException(ErrorCode.EMPTY_CARD_PATCH);
        }

        ConsultationSession session = require(patch.consultationId());
        if (session.getStatus() == ConsultationStatus.URGENT) {
            throw new BusinessException(ErrorCode.URGENT_STOP);
        }
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

        if (descriptionChanged && safetyGate.triggered(description)) {
            session.setStatus(ConsultationStatus.URGENT);
            session.setCard(null);
            store.save(session);
            return urgentView(session);
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

    public ConsultationRes confirm(String consultationId) {
        ConsultationSession session = require(consultationId);
        if (session.getStatus() == ConsultationStatus.URGENT) {
            throw new BusinessException(ErrorCode.URGENT_STOP);
        }
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

    private ConsultationSession findOrCreate(String consultationId) {
        if (consultationId == null || consultationId.isBlank()) {
            ConsultationSession created = new ConsultationSession(UUID.randomUUID().toString());
            store.save(created);
            return created;
        }
        return require(consultationId);
    }

    private ConsultationSession require(String consultationId) {
        ConsultationSession session = store.find(consultationId);
        if (session == null) {
            throw new BusinessException(ErrorCode.CONSULTATION_NOT_FOUND);
        }
        return session;
    }

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

    private void addDescription(ConsultationSession session, String sentence) {
        if (session.getDescription().isEmpty()) {
            session.setDescription(sentence);
            return;
        }
        session.setDescription(session.getDescription() + "\n" + sentence);
    }

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

    private ConsultationRes blankView(ConsultationSession session) {
        if (session.getStatus() == ConsultationStatus.NEED_SLOT) {
            MissingSlot missing = nextMissing(session);
            return new ConsultationRes(session.getId(), ConsultationStatus.NEED_SLOT, REDESCRIBE_PROMPT, missing, null);
        }
        return new ConsultationRes(
                session.getId(), ConsultationStatus.NEED_DESCRIPTION, REDESCRIBE_PROMPT, null, null);
    }

    private ConsultationRes urgentView(ConsultationSession session) {
        return new ConsultationRes(session.getId(), ConsultationStatus.URGENT, URGENT_PROMPT, null, null);
    }

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

    private String promptFor(MissingSlot slot) {
        if (slot == MissingSlot.GENDER) {
            return "请补充性别。";
        }
        if (slot == MissingSlot.AGE) {
            return "请补充年龄。";
        }
        return "请补充病程。";
    }

    private boolean hasCardChange(CardPatchReq patch) {
        return patch.gender() != null
                || patch.age() != null
                || patch.duration() != null
                || patch.description() != null
                || patch.department() != null;
    }
}
