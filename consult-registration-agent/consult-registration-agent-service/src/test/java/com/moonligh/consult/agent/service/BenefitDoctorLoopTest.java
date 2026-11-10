package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.moonligh.benefit.BenefitBook;
import com.moonligh.benefit.BenefitLine;
import com.moonligh.benefit.Issue;
import com.moonligh.benefit.MemoryBenefitStore;
import com.moonligh.benefit.Product;
import com.moonligh.benefit.ProductItem;
import com.moonligh.benefit.Quote;
import com.moonligh.benefit.Reserve;
import com.moonligh.consult.agent.api.enums.PayStatus;
import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.BenefitItemRes;
import com.moonligh.consult.agent.api.res.ConsultationDetailRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.agent.domain.benefit.BenefitGateway;
import com.moonligh.consult.agent.domain.benefit.BenefitQuote;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

class BenefitDoctorLoopTest {

    @Test
    void benefitPaysThenDoctorRepliesAndQuotaStaysSpent() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        BenefitBook book = new BenefitBook(new MemoryBenefitStore());
        book.define(new Product("TRIAL", "体验卡", 30, List.of(new ProductItem("P1", new BigDecimal("1")))));
        book.issue(new Issue("acc-1", "TRIAL", "ops", "loop-1"), Instant.parse("2026-11-05T10:00:00Z"));
        BookGateway benefit = new BookGateway(book);
        fixture.service.setBenefit(benefit);
        fixture.service.setDoctorAssigner(fixedDoctor());
        String id = fixture.service.acceptMessage(null, null, "女，32岁，干咳半年，运动后加重", "acc-1").consultationId();
        fixture.service.confirm(id);
        ConsultationRes paid = fixture.service.placeOrder(id, 1, 15, null);
        assertEquals(PayStatus.PAID, paid.order().payStatus());
        assertEquals(0, paid.order().payAmount().signum());
        assertEquals("doc-1", paid.order().doctor().id());
        assertEquals(new BigDecimal("0.00"), remaining(book, "P1"));
        assertThrows(BusinessException.class, () -> fixture.service.doctorReply(id, "other", "不是我的单"));
        ConsultationDetailRes reply = fixture.service.doctorReply(id, "doc-1", "已看到病历，先按慢病随访。");
        assertTrue(reply.lines().stream().anyMatch(line -> line.source() == 3 && line.text().contains("慢病随访")));
        fixture.service.endConsult(id, "ENDED");
        assertFalse(benefit.restored);
        assertEquals(new BigDecimal("0.00"), remaining(book, "P1"));
    }

    @Test
    void wrongStateCannotReply() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        String id = fixture.service.acceptMessage(null, null, "女，32岁，干咳半年，运动后加重", "acc-1").consultationId();
        BusinessException error = assertThrows(BusinessException.class, () -> fixture.service.doctorReply(id, "doc-1", "你好"));
        assertEquals(ErrorCode.ORDER_NOT_ALLOWED, error.getErrorCode());
    }

    private static DoctorAssigner fixedDoctor() {
        return (consultationId, orderId, card, terms) -> new AssignedDoctorRes(
                "doc-1", "周医生", "主治医师", "市医院", 1, "呼吸内科", null, "干咳", "sch-1");
    }

    private static BigDecimal remaining(BenefitBook book, String item) {
        return book.account("acc-1", Instant.parse("2026-11-05T10:05:00Z")).stream()
                .filter(line -> item.equals(line.itemCode()))
                .map(BenefitLine::remaining)
                .findFirst()
                .orElseThrow();
    }

    private static final class BookGateway implements BenefitGateway {

        private final BenefitBook book;
        private boolean restored;

        private BookGateway(BenefitBook book) {
            this.book = book;
        }

        @Override
        public boolean enabled() {
            return true;
        }

        @Override
        public BenefitQuote reserve(String accountId, String orderId, int packageCode, BigDecimal catalogPay) {
            Quote quote = book.reserve(new Reserve(accountId, orderId, packageCode, catalogPay), Instant.now());
            return new BenefitQuote(quote.cardId(), quote.benefitAmount(), quote.cashDue());
        }

        @Override
        public void confirm(String orderId) {
            book.confirm(orderId);
        }

        @Override
        public void release(String orderId) {
            book.release(orderId);
        }

        @Override
        public void restore(String orderId) {
            restored = true;
            book.restore(orderId);
        }

        @Override
        public List<BenefitItemRes> account(String accountId) {
            return new ArrayList<>();
        }
    }
}
