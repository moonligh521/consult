package com.moonligh.consult.agent.infrastructure.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.config.PayProperties;
import com.moonligh.consult.agent.domain.pay.PayCommand;
import com.moonligh.consult.agent.domain.pay.PaymentTicket;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.agent.service.ConsultationsFixture;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

class ChannelPaymentCashierTest {

    @Test
    void blankChannelStaysDemo() {
        PaymentTicket ticket = new ChannelPaymentCashier(new PayProperties())
                .collect(new PayCommand("c1", "o1", new BigDecimal("3.00"), "在线问诊", null));
        assertTrue(ticket.paid());
        assertEquals("demo", ticket.channel());
    }

    @Test
    void wechatWithoutMerchantIsNotReady() {
        BusinessException error = assertThrows(BusinessException.class, () -> new ChannelPaymentCashier(new PayProperties())
                .collect(new PayCommand("c1", "o1", new BigDecimal("3.00"), "在线问诊", "wechat")));
        assertEquals(ErrorCode.PAY_CHANNEL_NOT_READY, error.getErrorCode());
    }

    @Test
    void realChannelReturnsPayloadAndDoesNotAssign() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        ConsultationService service = fixture.service;
        service.setCashier(command -> new PaymentTicket(false, "wechat", "weixin://pay"));
        String id = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重").consultationId();
        service.confirm(id);
        ConsultationRes view = service.placeOrder(id, 3, 15, "wechat");
        assertEquals(PayStatus.PENDING, view.order().payStatus());
        assertEquals("weixin://pay", view.order().payPayload());
        assertEquals("wechat", view.order().payChannel());
        assertEquals(null, view.order().endsAt());
        assertFalse(fixture.repository.listUnmatchedPaid(10).stream()
                .anyMatch(entry -> id.equals(entry.consultationId())));
    }
}
