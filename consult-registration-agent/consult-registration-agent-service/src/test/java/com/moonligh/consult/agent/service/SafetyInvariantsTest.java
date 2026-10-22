package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.SlotParser;
import com.moonligh.consult.agent.domain.SymptomExtractor;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * INV-001～013：急症锁、改卡闲门、DRAFT 影子、科室依据、问诊关闭。
 */
class SafetyInvariantsTest {

    @Test
    void inv001ChestPainIsUrgent() {
        ConsultationRes view = ConsultationsFixture.shadow().service.acceptMessage(null, "胸痛");
        assertEquals(ConsultationStatus.URGENT, view.status());
    }

    @Test
    void inv002DyspneaIsUrgent() {
        ConsultationRes view = ConsultationsFixture.shadow().service.acceptMessage(null, "呼吸困难");
        assertEquals(ConsultationStatus.URGENT, view.status());
    }

    @Test
    void inv003UrgentStaysLatched() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        String id = fixture.service.acceptMessage(null, "胸痛").consultationId();
        ConsultationRes again = fixture.service.acceptMessage(id, "已经不痛了");
        assertEquals(ConsultationStatus.URGENT, again.status());
    }

    @Test
    void inv004UrgentRejectsCardConfirmAndOrder() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        String id = fixture.service.acceptMessage(null, "胸痛").consultationId();
        assertEquals(ErrorCode.URGENT_STOP, stopCode(() -> fixture.service.updateCard(
                new CardPatchReq(id, null, null, null, "腹痛", null))));
        assertEquals(ErrorCode.URGENT_STOP, stopCode(() -> fixture.service.confirm(id)));
        assertEquals(ErrorCode.URGENT_STOP, stopCode(() -> fixture.service.placeOrder(id, 1)));
    }

    @Test
    void inv005HumanRejectsCardConfirmAndOrder() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        String id = fixture.service.acceptMessage(null, "转人工").consultationId();
        assertEquals(ErrorCode.HUMAN_STOP, stopCode(() -> fixture.service.updateCard(
                new CardPatchReq(id, null, null, null, "腹痛", null))));
        assertEquals(ErrorCode.HUMAN_STOP, stopCode(() -> fixture.service.confirm(id)));
        assertEquals(ErrorCode.HUMAN_STOP, stopCode(() -> fixture.service.placeOrder(id, 1)));
    }

    @Test
    void inv006NegatedChestPainStillUrgent() {
        ConsultationRes view = ConsultationsFixture.shadow().service.acceptMessage(null, "没有胸痛");
        assertEquals(ConsultationStatus.URGENT, view.status());
    }

    @Test
    void inv007aDraftChestPainTypoDoesNotUpgradeToBaseline() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationRes view = fixture.service.acceptMessage(null, "女，32岁，胸疼两小时");
        assertFalse(view.status() == ConsultationStatus.URGENT);
        assertTrue(fixture.audits.list(view.consultationId()).stream()
                .anyMatch(audit -> audit.shadowRuleIds().contains("URG-DRAFT-001")));
    }

    @Test
    void inv007DraftStrokeOnlyShadows() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationRes view = fixture.service.acceptMessage(null, "女，32岁，口角歪斜两小时");
        assertFalse(view.status() == ConsultationStatus.URGENT);
        assertTrue(fixture.audits.list(view.consultationId()).stream()
                .anyMatch(audit -> audit.shadowRuleIds().contains("URG-DRAFT-003")));
    }

    @Test
    void inv008EnforceDraftStrokeIsUrgent() {
        ConsultationRes view = ConsultationsFixture.enforce().service.acceptMessage(null, "口角歪斜");
        assertEquals(ConsultationStatus.URGENT, view.status());
    }

    @Test
    void inv009VagueComplaintStaysOnDescription() {
        ConsultationRes view = ConsultationsFixture.shadow().service.acceptMessage(null, "有点不舒服");
        assertEquals(ConsultationStatus.NEED_DESCRIPTION, view.status());
        assertNull(view.missingSlot());
        assertNull(view.card());
    }

    @Test
    void inv010InquiryClosedRejectsConfirmAndOrder() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        InquiryProperties closed = new InquiryProperties();
        closed.setClosed(true);
        ConsultationService blocked = new ConsultationService(
                fixture.repository,
                fixture.gate,
                new SlotParser(),
                new SymptomExtractor(),
                new DepartmentRouter(),
                fixture.metrics,
                closed);
        String id = blocked.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        assertEquals(ErrorCode.INQUIRY_CLOSED, stopCode(() -> blocked.confirm(id)));

        ConsultationsFixture open = ConsultationsFixture.shadow();
        String paidId = open.service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        open.service.confirm(paidId);
        ConsultationService closedPay = new ConsultationService(
                open.repository,
                open.gate,
                new SlotParser(),
                new SymptomExtractor(),
                new DepartmentRouter(),
                open.metrics,
                closed);
        assertEquals(ErrorCode.INQUIRY_CLOSED, stopCode(() -> closedPay.placeOrder(paidId, 1)));
    }

    @Test
    void inv011DepartmentEvidenceIsRecorded() {
        ConsultationRes view = ConsultationsFixture.shadow().service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        assertEquals(Department.INTERNAL, view.card().department());
        assertEquals("呼吸内科", view.card().specialty());
        assertEquals("干咳", view.card().departmentEvidence());
    }

    @Test
    void inv012OptionalCardFactsFromDescription() {
        ConsultationRes view = ConsultationsFixture.shadow().service.acceptMessage(
                null, "女，32岁，干咳半年，青霉素过敏，在吃降压药");
        assertEquals("青霉素过敏", view.card().allergy());
        assertEquals("降压药", view.card().medications());
        assertTrue(view.card().associatedSymptoms().isEmpty());
    }

    @Test
    void inv013UnsuitableRejectsOrder() {
        ConsultationsFixture fixture = ConsultationsFixture.enforce();
        ConsultationRes view = fixture.service.acceptMessage(null, null, 2, "女，32岁，需要线下拍片两天");
        assertEquals(ConsultationStatus.UNSUITABLE_FOR_ONLINE, view.status());
        assertEquals(ErrorCode.UNSUITABLE_STOP, stopCode(() -> fixture.service.placeOrder(view.consultationId(), 1)));
    }

    @Test
    void inv014ConfirmedChestPainStillBlocksOrder() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        String id = fixture.service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        fixture.service.confirm(id);
        ConsultationRes urgent = fixture.service.acceptMessage(id, "我现在胸痛");
        assertEquals(ConsultationStatus.URGENT, urgent.status());
        assertEquals(ErrorCode.URGENT_STOP, stopCode(() -> fixture.service.placeOrder(id, 1)));
    }

    @Test
    void inv015SameMessageIdDoesNotDuplicateAndKeepsView() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationRes first = fixture.service.acceptMessage(null, "m-1", null, "女，32岁，干咳半年，运动后加重");
        ConsultationRes again = fixture.service.acceptMessage(first.consultationId(), "m-1", null, "女，32岁，干咳半年，运动后加重");
        assertEquals(first.status(), again.status());
        assertEquals(first.consultationId(), again.consultationId());
        assertEquals(1, fixture.repository.getRequired(first.consultationId()).messages().size());
    }

    @Test
    void inv016UnclearSpeechOnlyShadows() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationRes view = fixture.service.acceptMessage(null, "女，32岁，说话不清两小时");
        assertFalse(view.status() == ConsultationStatus.URGENT);
        assertTrue(fixture.audits.list(view.consultationId()).stream()
                .anyMatch(audit -> audit.shadowRuleIds().contains("URG-DRAFT-003")));
    }

    private static ErrorCode stopCode(Runnable action) {
        return assertThrows(BusinessException.class, action::run).getErrorCode();
    }
}
