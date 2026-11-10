package com.moonligh.benefit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 单测用的内存账。 */
public final class MemoryBenefitStore implements BenefitStore {

    private final Map<String, Product> products = new LinkedHashMap<>();
    private final Map<String, NewCard> cards = new LinkedHashMap<>();
    private final Map<String, String> sourceIndex = new LinkedHashMap<>();
    private final Map<String, BigDecimal> remaining = new LinkedHashMap<>();
    private final Map<String, Hold> holds = new LinkedHashMap<>();

    @Override
    public void saveProduct(Product product) {
        products.put(product.code(), product);
    }

    @Override
    public Product findProduct(String code) {
        return products.get(code);
    }

    @Override
    public Issued findBySource(String channel, String sourceRef) {
        String cardId = sourceIndex.get(channel + "\n" + sourceRef);
        if (cardId == null) {
            return null;
        }
        NewCard card = cards.get(cardId);
        return new Issued(card.id(), card.accountId(), card.productCode());
    }

    @Override
    public void insertCard(NewCard card) {
        cards.put(card.id(), card);
        sourceIndex.put(card.channel() + "\n" + card.sourceRef(), card.id());
        for (Balance balance : card.balances()) {
            remaining.put(key(card.id(), balance.itemCode()), balance.remaining());
        }
    }

    @Override
    public Hold findHold(String orderId) {
        return holds.get(orderId);
    }

    @Override
    public void insertHold(Hold hold) {
        holds.put(hold.orderId(), hold);
    }

    @Override
    public void replaceHold(Hold hold) {
        holds.put(hold.orderId(), hold);
    }

    @Override
    public void deleteHold(String orderId) {
        holds.remove(orderId);
    }

    @Override
    public void updateStatus(String orderId, int status) {
        Hold hold = holds.get(orderId);
        holds.put(orderId, new Hold(
                hold.orderId(),
                hold.accountId(),
                hold.cardId(),
                hold.itemCode(),
                hold.holdCount(),
                hold.holdAmount(),
                hold.cashDue(),
                hold.catalogAmount(),
                status));
    }

    @Override
    public List<Bucket> lockAccount(String accountId, Instant now) {
        List<Bucket> rows = new ArrayList<>();
        for (NewCard card : cards.values()) {
            if (!accountId.equals(card.accountId()) || !card.validTo().isAfter(now)) {
                continue;
            }
            for (Balance balance : card.balances()) {
                rows.add(new Bucket(card.id(), card.validTo(), balance.itemCode(), money(remaining.get(key(card.id(), balance.itemCode())))));
            }
        }
        return rows;
    }

    @Override
    public boolean take(String cardId, String itemCode, BigDecimal amount) {
        String key = key(cardId, itemCode);
        BigDecimal left = remaining.get(key);
        if (left == null || left.compareTo(amount) < 0) {
            return false;
        }
        remaining.put(key, money(left.subtract(amount)));
        return true;
    }

    @Override
    public void giveBack(String cardId, String itemCode, BigDecimal amount) {
        String key = key(cardId, itemCode);
        remaining.put(key, money(remaining.get(key).add(amount)));
    }

    @Override
    public List<BenefitLine> lines(String accountId, Instant now) {
        List<BenefitLine> lines = new ArrayList<>();
        for (NewCard card : cards.values()) {
            if (!accountId.equals(card.accountId()) || !card.validTo().isAfter(now)) {
                continue;
            }
            for (Balance balance : card.balances()) {
                lines.add(new BenefitLine(
                        card.id(),
                        card.productCode(),
                        balance.itemCode(),
                        money(remaining.get(key(card.id(), balance.itemCode()))),
                        card.validTo()));
            }
        }
        return lines;
    }

    private static String key(String cardId, String itemCode) {
        return cardId + "\n" + itemCode;
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
