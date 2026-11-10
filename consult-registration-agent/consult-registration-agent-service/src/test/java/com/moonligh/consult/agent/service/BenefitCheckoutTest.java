package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.BenefitItemRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.benefit.BenefitGateway;
import com.moonligh.consult.agent.domain.benefit.BenefitQuote;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

class BenefitCheckoutTest {

    @Test
    void fullCoverSkipsCashAndMarksPaid() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        RecordingBenefit benefit = new RecordingBenefit(new BigDecimal("6.90"));
        fixture.service.setBenefit(benefit);
        String id = open(fixture);
        ConsultationRes view = fixture.service.placeOrder(id, 3, 15, null);
        assertEquals(PayStatus.PAID, view.order().payStatus());
        assertEquals(new BigDecimal("0.00"), view.order().payAmount().setScale(2));
        assertEquals(new BigDecimal("6.90"), view.order().benefitAmount().setScale(2));
        assertEquals("benefit", view.order().payChannel());
        assertTrue(benefit.calls.contains("confirm"));
        assertTrue(fixture.repository.benefitSettled(view.order().orderId()));
    }

    @Test
    void confirmFailureStaysUnsettledUntilRetry() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        RecordingBenefit benefit = new RecordingBenefit(new BigDecimal("6.90"));
        benefit.confirmFailure = new IllegalStateException("down");
        fixture.service.setBenefit(benefit);
        String id = open(fixture);
        ConsultationRes view = fixture.service.placeOrder(id, 3, 15, null);
        assertEquals(PayStatus.PAID, view.order().payStatus());
        assertFalse(fixture.repository.benefitSettled(view.order().orderId()));
        benefit.confirmFailure = null;
        assertEquals(1, fixture.service.reconcileBenefits());
        assertTrue(fixture.repository.benefitSettled(view.order().orderId()));
        assertEquals(0, fixture.service.reconcileBenefits());
    }

    @Test
    void exhaustedQuotaKeepsTheCatalogPrice() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        fixture.service.setBenefit(new RecordingBenefit(BigDecimal.ZERO));
        String id = open(fixture);
        ConsultationRes view = fixture.service.placeOrder(id, 3, 15, null);
        assertEquals(PayStatus.PAID, view.order().payStatus());
        assertEquals(new BigDecimal("6.90"), view.order().payAmount().setScale(2));
        assertEquals(0, view.order().benefitAmount().signum());
    }

    @Test
    void benefitOutageCancelsThePendingOrder() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        RecordingBenefit benefit = new RecordingBenefit(new BigDecimal("6.90"));
        benefit.failure = new IllegalStateException("down");
        fixture.service.setBenefit(benefit);
        String id = open(fixture);
        BusinessException error = assertThrows(BusinessException.class, () -> fixture.service.placeOrder(id, 3, 15, null));
        assertEquals(ErrorCode.BENEFIT_UNAVAILABLE, error.getErrorCode());
        assertTrue(benefit.calls.contains("release"));
        assertTrue(fixture.repository.get(id).getOrder().endedAt() != null);
    }

    private static String open(ConsultationsFixture fixture) {
        String id = fixture.service.acceptMessage(null, null, "女，32岁，干咳半年，运动后加重", "acc-1").consultationId();
        fixture.service.confirm(id);
        return id;
    }

    private static final class RecordingBenefit implements BenefitGateway {

        private final BigDecimal cover;
        private final List<String> calls = new ArrayList<>();
        private RuntimeException failure;
        private RuntimeException confirmFailure;

        private RecordingBenefit(BigDecimal cover) {
            this.cover = cover;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public BenefitQuote reserve(String accountId, String orderId, int packageCode, BigDecimal catalogPay) {
            calls.add("reserve");
            if (failure != null) {
                throw failure;
            }
            BigDecimal cash = catalogPay.subtract(cover);
            if (cash.signum() < 0) {
                cash = BigDecimal.ZERO;
            }
            return new BenefitQuote(cover.signum() == 0 ? null : "card-1", cover, cash);
        }

        @Override
        public void confirm(String orderId) {
            calls.add("confirm");
            if (confirmFailure != null) {
                throw confirmFailure;
            }
        }

        @Override
        public void release(String orderId) {
            calls.add("release");
        }

        @Override
        public void restore(String orderId) {
            calls.add("restore");
        }

        @Override
        public List<BenefitItemRes> account(String accountId) {
            return List.of();
        }
    }
}
