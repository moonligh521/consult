package com.moonligh.benefit.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.moonligh.benefit.domain.Balance;
import com.moonligh.benefit.domain.BenefitLine;
import com.moonligh.benefit.domain.BenefitStore;
import com.moonligh.benefit.domain.Bucket;
import com.moonligh.benefit.domain.Hold;
import com.moonligh.benefit.domain.Issued;
import com.moonligh.benefit.domain.NewCard;
import com.moonligh.benefit.domain.ProductHead;
import com.moonligh.benefit.domain.ProductItem;
import com.moonligh.benefit.domain.Product;

@Repository
public class BenefitStoreImpl implements BenefitStore {

    private final BenefitMapper benefits;

    public BenefitStoreImpl(BenefitMapper benefits) {
        this.benefits = benefits;
    }

    @Override
    public boolean serializesCaller() {
        return false;
    }

    @Override
    public void saveProduct(Product product) {
        benefits.upsertProduct(product.code(), product.name(), product.validDays());
        benefits.deleteItems(product.code());
        for (ProductItem item : product.items()) {
            benefits.insertItem(product.code(), item.itemCode(), item.quantity());
        }
    }

    @Override
    public Product findProduct(String code) {
        ProductHead head = benefits.findProduct(code);
        if (head == null) {
            return null;
        }
        return new Product(head.code(), head.name(), head.validDays(), benefits.findItems(code));
    }

    @Override
    public Issued findBySource(String channel, String sourceRef) {
        return benefits.findBySource(channel, sourceRef);
    }

    @Override
    public void insertCard(NewCard card) {
        benefits.insertCard(card);
        for (Balance balance : card.balances()) {
            benefits.insertBalance(card.id(), balance.itemCode(), balance.remaining());
        }
    }

    @Override
    public Hold findHold(String orderId) {
        return benefits.findHold(orderId);
    }

    @Override
    public void insertHold(Hold hold) {
        benefits.insertHold(hold);
    }

    @Override
    public void replaceHold(Hold hold) {
        benefits.replaceHold(hold);
    }

    @Override
    public void deleteHold(String orderId) {
        benefits.deleteHold(orderId);
    }

    @Override
    public void updateStatus(String orderId, int status) {
        benefits.updateStatus(orderId, status);
    }

    @Override
    public List<Bucket> lockAccount(String accountId, Instant now) {
        return benefits.lockAccount(accountId, now);
    }

    @Override
    public boolean take(String cardId, String itemCode, BigDecimal amount) {
        return benefits.take(cardId, itemCode, amount) == 1;
    }

    @Override
    public void giveBack(String cardId, String itemCode, BigDecimal amount) {
        benefits.giveBack(cardId, itemCode, amount);
    }

    @Override
    public List<BenefitLine> lines(String accountId, Instant now) {
        return benefits.lines(accountId, now);
    }
}
