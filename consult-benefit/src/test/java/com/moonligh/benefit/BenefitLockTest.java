package com.moonligh.benefit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class BenefitLockTest {

    @Test
    void databaseStoreLetsDifferentAccountsReserveTogether() throws Exception {
        CountDownLatch inside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        BenefitBook book = new BenefitBook(new ParkingStore(inside, release));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> book.reserve(new Reserve("acc-a", "order-a", 1, new BigDecimal("3.00")), Instant.EPOCH));
            Future<?> second = pool.submit(() -> book.reserve(new Reserve("acc-b", "order-b", 1, new BigDecimal("3.00")), Instant.EPOCH));
            assertTrue(inside.await(1, TimeUnit.SECONDS), "两个账号应同时进入预占");
            release.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private static final class ParkingStore implements BenefitStore {

        private final CountDownLatch inside;
        private final CountDownLatch release;

        private ParkingStore(CountDownLatch inside, CountDownLatch release) {
            this.inside = inside;
            this.release = release;
        }

        @Override
        public boolean serializesCaller() {
            return false;
        }

        @Override
        public void saveProduct(Product product) {
        }

        @Override
        public Product findProduct(String code) {
            return null;
        }

        @Override
        public Issued findBySource(String channel, String sourceRef) {
            return null;
        }

        @Override
        public void insertCard(NewCard card) {
        }

        @Override
        public Hold findHold(String orderId) {
            return null;
        }

        @Override
        public void insertHold(Hold hold) {
        }

        @Override
        public void replaceHold(Hold hold) {
        }

        @Override
        public void deleteHold(String orderId) {
        }

        @Override
        public void updateStatus(String orderId, int status) {
        }

        @Override
        public List<Bucket> lockAccount(String accountId, Instant now) {
            inside.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("预占被进程锁挡住");
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(error);
            }
            return new java.util.ArrayList<>();
        }

        @Override
        public boolean take(String cardId, String itemCode, BigDecimal amount) {
            return false;
        }

        @Override
        public void giveBack(String cardId, String itemCode, BigDecimal amount) {
        }

        @Override
        public List<BenefitLine> lines(String accountId, Instant now) {
            return List.of();
        }
    }
}
