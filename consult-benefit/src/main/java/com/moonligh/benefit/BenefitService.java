package com.moonligh.benefit;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BenefitService {

    private final BenefitBook book;

    public BenefitService(JdbcBenefitStore store) {
        this.book = new BenefitBook(store);
    }

    @Transactional
    public void define(Product product) {
        book.define(product);
    }

    @Transactional
    public Issued issue(Issue issue) {
        return book.issue(issue, Instant.now());
    }

    @Transactional
    public Quote reserve(Reserve reserve) {
        return book.reserve(reserve, Instant.now());
    }

    @Transactional
    public void confirm(String orderId) {
        book.confirm(orderId);
    }

    @Transactional
    public void release(String orderId) {
        book.release(orderId);
    }

    @Transactional
    public void restore(String orderId) {
        book.restore(orderId);
    }

    @Transactional(readOnly = true)
    public List<BenefitLine> account(String accountId) {
        return book.account(accountId, Instant.now());
    }
}
