package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.enums.MissingSlot;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/** AC-1～14：挂号主流程验收。用内存仓储，不连数据库。 */
class ConsultationAcceptanceTest {

    private ConsultationService service;

    @BeforeEach
    void setUp() {
        // 每条用例一套新的内存会话，互不影响。
        service = ConsultationsFixture.shadow().service;
    }

    @Test
    void ac1CompleteCard() {
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        assertEquals(ConsultationStatus.CARD_READY, view.status());
        assertNull(view.prompt());
        assertNull(view.missingSlot());
        assertEquals(Gender.FEMALE, view.card().gender());
        assertEquals(32, view.card().age());
        assertEquals("半年", view.card().duration());
        assertEquals(List.of("干咳"), view.card().symptoms());
        assertEquals(Department.RESPIRATORY, view.card().department());
        assertEquals("女，32岁，干咳半年，运动后加重", view.card().description());
    }

    @Test
    void ac2AsksAgeThenCompletes() {
        ConsultationRes first = service.acceptMessage(null, "男，干咳三天");
        assertEquals(ConsultationStatus.NEED_SLOT, first.status());
        assertEquals(MissingSlot.AGE, first.missingSlot());
        assertEquals("请补充年龄。", first.prompt());
        assertNull(first.card());

        ConsultationRes second = service.acceptMessage(first.consultationId(), "32岁");
        assertEquals(ConsultationStatus.CARD_READY, second.status());
        assertEquals(Gender.MALE, second.card().gender());
        assertEquals(32, second.card().age());
        assertEquals("三天", second.card().duration());
        assertEquals(List.of("干咳"), second.card().symptoms());
        assertEquals(Department.RESPIRATORY, second.card().department());
        assertEquals("男，干咳三天\n32岁", second.card().description());
    }

    @Test
    void ac3AsksDurationThenCompletes() {
        ConsultationRes first = service.acceptMessage(null, "女，28岁，头痛");
        assertEquals(ConsultationStatus.NEED_SLOT, first.status());
        assertEquals(MissingSlot.DURATION, first.missingSlot());
        assertEquals("请补充病程。", first.prompt());
        assertNull(first.card());

        ConsultationRes second = service.acceptMessage(first.consultationId(), "三天");
        assertEquals(ConsultationStatus.CARD_READY, second.status());
        assertEquals(Gender.FEMALE, second.card().gender());
        assertEquals(28, second.card().age());
        assertEquals("三天", second.card().duration());
        assertEquals("女，28岁，头痛\n三天", second.card().description());
    }

    @Test
    void ac4UrgentChestPainStaysUrgent() {
        ConsultationRes first = service.acceptMessage(null, "45岁，胸痛两小时");
        assertEquals(ConsultationStatus.URGENT, first.status());
        assertEquals("请立即拨打 120，或前往最近的实体医院急诊。", first.prompt());
        assertNull(first.missingSlot());
        assertNull(first.card());

        ConsultationRes again = service.acceptMessage(first.consultationId(), "还痛");
        assertEquals(ConsultationStatus.URGENT, again.status());
        assertEquals("请立即拨打 120，或前往最近的实体医院急诊。", again.prompt());
        assertNull(again.card());

        BusinessException patch = assertThrows(
                BusinessException.class,
                () -> service.updateCard(new CardPatchReq(first.consultationId(), null, null, null, "腹痛", null)));
        assertEquals(ErrorCode.URGENT_STOP, patch.getErrorCode());
        assertEquals("请先寻求紧急医疗帮助。", patch.getMessage());

        BusinessException confirm = assertThrows(
                BusinessException.class,
                () -> service.confirm(first.consultationId()));
        assertEquals(ErrorCode.URGENT_STOP, confirm.getErrorCode());
    }

    @Test
    void ac8DyspneaIsUrgentNotRespiratory() {
        ConsultationRes view = service.acceptMessage(null, "呼吸困难");
        assertEquals(ConsultationStatus.URGENT, view.status());
        assertEquals("请立即拨打 120，或前往最近的实体医院急诊。", view.prompt());
        assertNull(view.card());
    }

    @Test
    void ac5OverrideDepartmentThenConfirm() {
        String id = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        ConsultationRes patched = service.updateCard(new CardPatchReq(id, null, null, null, null, Department.GENERAL));
        assertEquals(ConsultationStatus.CARD_READY, patched.status());
        assertEquals(Department.GENERAL, patched.card().department());
        assertEquals(List.of("干咳"), patched.card().symptoms());

        ConsultationRes confirmed = service.confirm(id);
        assertEquals(ConsultationStatus.CONFIRMED, confirmed.status());
        assertEquals(Department.GENERAL, confirmed.card().department());
        assertEquals(List.of("干咳"), confirmed.card().symptoms());
        assertNull(confirmed.prompt());
        assertNull(confirmed.missingSlot());
    }

    @Test
    void ac6RewriteDescriptionRecalculatesDepartment() {
        String id = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        ConsultationRes patched = service.updateCard(new CardPatchReq(id, null, null, null, "腹痛两天", null));
        assertEquals(ConsultationStatus.CARD_READY, patched.status());
        assertEquals("腹痛两天", patched.card().description());
        assertEquals(List.of("腹痛"), patched.card().symptoms());
        assertEquals(Department.DIGESTIVE, patched.card().department());
        assertEquals(Gender.FEMALE, patched.card().gender());
        assertEquals(32, patched.card().age());
        assertEquals("半年", patched.card().duration());
    }

    @Test
    void ac7BlankAsksToRedescribe() {
        ConsultationRes view = service.acceptMessage(null, "  ");
        assertNotNull(view.consultationId());
        assertEquals(ConsultationStatus.NEED_DESCRIPTION, view.status());
        assertEquals("请重新描述病情。", view.prompt());
        assertNull(view.missingSlot());
        assertNull(view.card());
    }

    @Test
    void unknownConsultationIsNotFound() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.acceptMessage("6f1c3a2e-4b7d-4e1a-9c2f-0a1b2c3d4e5f", "女"));
        assertEquals(ErrorCode.CONSULTATION_NOT_FOUND, error.getErrorCode());
        assertEquals("找不到这轮咨询。", error.getMessage());
    }

    @Test
    void ac9FractureStillGetsCardForOnlineDoctor() {
        ConsultationRes view = service.acceptMessage(null, "女，32岁，手腕骨折两天");
        assertEquals(ConsultationStatus.CARD_READY, view.status());
        assertNull(view.prompt());
        assertEquals(Gender.FEMALE, view.card().gender());
        assertEquals(32, view.card().age());
        assertEquals("两天", view.card().duration());
        assertEquals(Department.GENERAL, view.card().department());

        ConsultationRes confirmed = service.confirm(view.consultationId());
        assertEquals(ConsultationStatus.CONFIRMED, confirmed.status());
        assertEquals(Department.GENERAL, confirmed.card().department());
    }

    @Test
    void ac10UrgentBeatsAskForHuman() {
        ConsultationRes view = service.acceptMessage(null, "胸痛并且转人工");
        assertEquals(ConsultationStatus.URGENT, view.status());
        assertEquals("请立即拨打 120，或前往最近的实体医院急诊。", view.prompt());
        assertNull(view.card());
    }

    @Test
    void ac11AskForHumanStopsAutoRouting() {
        ConsultationRes first = service.acceptMessage(null, "转人工");
        assertEquals(ConsultationStatus.NEED_HUMAN, first.status());
        assertEquals("请转人工协助。当前无法自动完成导诊。", first.prompt());
        assertNull(first.card());

        ConsultationRes again = service.acceptMessage(first.consultationId(), "还是要人工");
        assertEquals(ConsultationStatus.NEED_HUMAN, again.status());
        assertNull(again.card());

        BusinessException patch = assertThrows(
                BusinessException.class,
                () -> service.updateCard(new CardPatchReq(first.consultationId(), null, null, null, "腹痛", null)));
        assertEquals(ErrorCode.HUMAN_STOP, patch.getErrorCode());

        BusinessException confirm = assertThrows(
                BusinessException.class,
                () -> service.confirm(first.consultationId()));
        assertEquals(ErrorCode.HUMAN_STOP, confirm.getErrorCode());
    }

    @Test
    void ac12FindHumanAlsoStops() {
        ConsultationRes view = service.acceptMessage(null, "找人工");
        assertEquals(ConsultationStatus.NEED_HUMAN, view.status());
        assertNull(view.card());
    }

    @Test
    void ac13PregnantCoughGoesOrdinaryTriage() {
        ConsultationRes view = service.acceptMessage(null, "孕妇，干咳半年");
        assertEquals(ConsultationStatus.NEED_SLOT, view.status());
        assertEquals(MissingSlot.GENDER, view.missingSlot());
        assertEquals("请补充性别。", view.prompt());
        assertNull(view.card());
    }

    @Test
    void ac14RewriteCardToHumanVoidsCard() {
        String id = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        ConsultationRes patched = service.updateCard(new CardPatchReq(id, null, null, null, "转人工", null));
        assertEquals(ConsultationStatus.NEED_HUMAN, patched.status());
        assertEquals("请转人工协助。当前无法自动完成导诊。", patched.prompt());
        assertNull(patched.card());
    }
}
