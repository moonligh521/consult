package com.moonligh.benefit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BenefitCoreTest {

    private static final Instant NOW = Instant.parse("2026-11-05T09:00:00Z");

    private BenefitBook book;

    @BeforeEach
    void setUp() {
        book = new BenefitBook(new MemoryBenefitStore());
        book.define(new Product("CHRONIC_PLUS", "慢病升级卡", 365, List.of(
                new ProductItem("P1", new BigDecimal("12")),
                new ProductItem("P3", new BigDecimal("4")),
                new ProductItem("P4", new BigDecimal("1")))));
        book.define(new Product("CREDIT", "抵扣金卡", 180, List.of(
                new ProductItem("CREDIT", new BigDecimal("4.00")))));
        book.define(new Product("SOON", "快到期", 10, List.of(
                new ProductItem("P3", new BigDecimal("1")))));
    }

    @Test
    void countCoversTheMatchingPackageOnly() {
        book.issue(new Issue("acc-1", "CHRONIC_PLUS", "ops", "ref-1"), NOW);
        Quote first = book.reserve(new Reserve("acc-1", "order-1", 3, new BigDecimal("6.90")), NOW);
        assertEquals(new BigDecimal("0.00"), first.cashDue());
        assertEquals(new BigDecimal("6.90"), first.benefitAmount());
        assertEquals("P3", first.itemCode());
        Quote again = book.reserve(new Reserve("acc-1", "order-1", 3, new BigDecimal("6.90")), NOW);
        assertEquals(first.cardId(), again.cardId());
        assertEquals(new BigDecimal("3.00"), remaining("acc-1", "P3"));
        assertEquals(new BigDecimal("12.00"), remaining("acc-1", "P1"));
    }

    @Test
    void lastCountGoesToOneOrder() {
        book.define(new Product("ONCE", "一次", 30, List.of(new ProductItem("P1", new BigDecimal("1")))));
        book.issue(new Issue("acc-1", "ONCE", "ops", "once"), NOW);
        Quote taken = book.reserve(new Reserve("acc-1", "order-a", 1, new BigDecimal("3.00")), NOW);
        Quote cash = book.reserve(new Reserve("acc-1", "order-b", 1, new BigDecimal("3.00")), NOW);
        assertEquals(new BigDecimal("0.00"), taken.cashDue());
        assertNull(cash.cardId());
        assertEquals(new BigDecimal("3.00"), cash.cashDue());
    }

    @Test
    void earlierCardIsUsedFirst() {
        Issued later = book.issue(new Issue("acc-1", "CHRONIC_PLUS", "ops", "later"), NOW);
        Issued sooner = book.issue(new Issue("acc-1", "SOON", "ops", "sooner"), NOW.minus(1, ChronoUnit.DAYS));
        Quote quote = book.reserve(new Reserve("acc-1", "order-1", 3, new BigDecimal("6.90")), NOW);
        assertEquals(sooner.cardId(), quote.cardId());
        assertEquals(new BigDecimal("4.00"), balance(later.cardId(), "P3"));
    }

    @Test
    void creditCoversPartOfThePrice() {
        book.issue(new Issue("acc-2", "CREDIT", "mall", "c-1"), NOW);
        Quote quote = book.reserve(new Reserve("acc-2", "order-9", 3, new BigDecimal("6.90")), NOW);
        assertEquals(new BigDecimal("4.00"), quote.benefitAmount());
        assertEquals(new BigDecimal("2.90"), quote.cashDue());
        assertEquals(new BigDecimal("0.00"), remaining("acc-2", "CREDIT"));
    }

    @Test
    void releaseReturnsAHoldAndRestoreReturnsAConfirmedUse() {
        book.issue(new Issue("acc-1", "CHRONIC_PLUS", "ops", "ref-2"), NOW);
        book.reserve(new Reserve("acc-1", "order-1", 4, new BigDecimal("23.90")), NOW);
        book.release("order-1");
        assertEquals(new BigDecimal("1.00"), remaining("acc-1", "P4"));
        book.reserve(new Reserve("acc-1", "order-2", 4, new BigDecimal("23.90")), NOW);
        book.confirm("order-2");
        book.release("order-2");
        assertEquals(new BigDecimal("0.00"), remaining("acc-1", "P4"));
        book.restore("order-2");
        assertEquals(new BigDecimal("1.00"), remaining("acc-1", "P4"));
        book.restore("order-2");
        assertEquals(new BigDecimal("1.00"), remaining("acc-1", "P4"));
    }

    @Test
    void releasedOrderCanBeReservedAgain() {
        book.issue(new Issue("acc-1", "CHRONIC_PLUS", "ops", "ref-3"), NOW);
        book.reserve(new Reserve("acc-1", "order-1", 4, new BigDecimal("23.90")), NOW);
        book.release("order-1");
        Quote again = book.reserve(new Reserve("acc-1", "order-1", 4, new BigDecimal("23.90")), NOW);
        assertEquals(new BigDecimal("0.00"), again.cashDue());
        assertEquals(new BigDecimal("0.00"), remaining("acc-1", "P4"));
    }

    @Test
    void sameChannelRefReturnsTheSameCard() {
        Issued first = book.issue(new Issue("acc-1", "CHRONIC_PLUS", "ops", "same"), NOW);
        Issued second = book.issue(new Issue("acc-1", "CHRONIC_PLUS", "ops", "same"), NOW.plusSeconds(5));
        assertEquals(first.cardId(), second.cardId());
        assertThrows(BenefitException.class,
                () -> book.issue(new Issue("acc-9", "CHRONIC_PLUS", "ops", "same"), NOW));
    }

    private BigDecimal remaining(String accountId, String itemCode) {
        return book.account(accountId, NOW).stream()
                .filter(line -> itemCode.equals(line.itemCode()))
                .map(BenefitLine::remaining)
                .findFirst()
                .orElseThrow();
    }

    private BigDecimal balance(String cardId, String itemCode) {
        return book.account("acc-1", NOW).stream()
                .filter(line -> cardId.equals(line.cardId()) && itemCode.equals(line.itemCode()))
                .map(BenefitLine::remaining)
                .findFirst()
                .orElseThrow();
    }
}
