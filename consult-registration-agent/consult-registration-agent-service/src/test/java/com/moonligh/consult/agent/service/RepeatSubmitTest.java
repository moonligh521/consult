package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.domain.MemoryOpeningClaim;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;

/** 前端连点或每次换编号重试时，服务端仍只落一次。 */
class RepeatSubmitTest {

    @Test
    void sameSentenceWithANewMessageIdDoesNotAppendAgain() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationRes first = fixture.service.acceptMessage(null, "m-1", "女，32岁，干咳半年", "acct-1");
        fixture.service.acceptMessage(first.consultationId(), "m-2", "女，32岁，干咳半年", "acct-1");
        assertEquals(1, fixture.repository.getRequired(first.consultationId()).messages().size());
    }

    @Test
    void openingTheSameComplaintTwiceReusesTheConsultation() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationRes first = fixture.service.acceptMessage(null, null, "女，32岁，干咳半年", "acct-1");
        ConsultationRes again = fixture.service.acceptMessage(null, null, "女，32岁，干咳半年", "acct-1");
        assertEquals(first.consultationId(), again.consultationId());
        assertEquals(1, fixture.repository.getRequired(first.consultationId()).messages().size());
    }

    @Test
    void openingWaitsWhenAnotherRequestAlreadyHoldsTheSentence() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        MemoryOpeningClaim claims = new MemoryOpeningClaim();
        claims.claim("acct-1", "女，32岁，干咳半年", "still-creating", Instant.now());
        fixture.service.setOpeningClaim(claims);
        BusinessException busy = assertThrows(
                BusinessException.class,
                () -> fixture.service.acceptMessage(null, null, "女，32岁，干咳半年", "acct-1"));
        assertEquals(ErrorCode.CONSULT_IN_PROGRESS, busy.getErrorCode());
    }

    @Test
    void savingTheSameCardDescriptionDoesNotAppendAgain() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationRes ready = fixture.service.acceptMessage(null, "女，32岁，干咳半年");
        fixture.service.updateCard(new CardPatchReq(ready.consultationId(), null, null, null, "干咳加重一周", null));
        int afterFirst = fixture.repository.getRequired(ready.consultationId()).messages().size();
        fixture.service.updateCard(new CardPatchReq(ready.consultationId(), null, null, null, "干咳加重一周", null));
        assertEquals(afterFirst, fixture.repository.getRequired(ready.consultationId()).messages().size());
    }
}
