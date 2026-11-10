package com.moonligh.benefit;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcBenefitStore implements BenefitStore {

    private final JdbcTemplate jdbc;

    public JdbcBenefitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean serializesCaller() {
        return false;
    }

    @Override
    public void saveProduct(Product product) {
        jdbc.update(
                """
                INSERT INTO benefit_product (code, name, valid_days, status)
                VALUES (?, ?, ?, 1) AS incoming
                ON DUPLICATE KEY UPDATE
                    name = incoming.name,
                    valid_days = incoming.valid_days,
                    status = 1
                """,
                product.code(),
                product.name(),
                product.validDays());
        jdbc.update("DELETE FROM benefit_product_item WHERE product_code = ?", product.code());
        for (ProductItem item : product.items()) {
            jdbc.update(
                    "INSERT INTO benefit_product_item (product_code, item_code, quantity) VALUES (?, ?, ?)",
                    product.code(),
                    item.itemCode(),
                    item.quantity());
        }
    }

    @Override
    public Product findProduct(String code) {
        List<Product> rows = jdbc.query(
                "SELECT code, name, valid_days FROM benefit_product WHERE code = ? AND status = 1",
                (rs, rowNum) -> new Product(rs.getString("code"), rs.getString("name"), rs.getInt("valid_days"), List.of()),
                code);
        if (rows.isEmpty()) {
            return null;
        }
        List<ProductItem> items = jdbc.query(
                "SELECT item_code, quantity FROM benefit_product_item WHERE product_code = ? ORDER BY item_code",
                (rs, rowNum) -> new ProductItem(rs.getString("item_code"), rs.getBigDecimal("quantity")),
                code);
        Product product = rows.get(0);
        return new Product(product.code(), product.name(), product.validDays(), items);
    }

    @Override
    public Issued findBySource(String channel, String sourceRef) {
        List<Issued> rows = jdbc.query(
                "SELECT id, account_id, product_code FROM benefit_card WHERE source_channel = ? AND source_ref = ?",
                (rs, rowNum) -> new Issued(rs.getString("id"), rs.getString("account_id"), rs.getString("product_code")),
                channel,
                sourceRef);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insertCard(NewCard card) {
        jdbc.update(
                """
                INSERT INTO benefit_card
                (id, account_id, product_code, source_channel, source_ref, status, valid_from, valid_to)
                VALUES (?, ?, ?, ?, ?, 1, ?, ?)
                """,
                card.id(),
                card.accountId(),
                card.productCode(),
                card.channel(),
                card.sourceRef(),
                Timestamp.from(card.validFrom()),
                Timestamp.from(card.validTo()));
        for (Balance balance : card.balances()) {
            jdbc.update(
                    "INSERT INTO benefit_balance (card_id, item_code, remaining) VALUES (?, ?, ?)",
                    card.id(),
                    balance.itemCode(),
                    balance.remaining());
        }
    }

    @Override
    public Hold findHold(String orderId) {
        List<Hold> rows = jdbc.query(
                """
                SELECT order_id, account_id, card_id, item_code, hold_count, hold_amount, cash_due, catalog_amount, status
                FROM benefit_hold WHERE order_id = ? FOR UPDATE
                """,
                this::mapHold,
                orderId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void insertHold(Hold hold) {
        jdbc.update(
                """
                INSERT INTO benefit_hold
                (order_id, account_id, card_id, item_code, hold_count, hold_amount, cash_due, catalog_amount, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                hold.orderId(),
                hold.accountId(),
                hold.cardId(),
                hold.itemCode(),
                hold.holdCount(),
                hold.holdAmount(),
                hold.cashDue(),
                hold.catalogAmount(),
                hold.status());
    }

    @Override
    public void replaceHold(Hold hold) {
        jdbc.update(
                """
                UPDATE benefit_hold
                SET account_id = ?, card_id = ?, item_code = ?, hold_count = ?, hold_amount = ?,
                    cash_due = ?, catalog_amount = ?, status = ?
                WHERE order_id = ?
                """,
                hold.accountId(),
                hold.cardId(),
                hold.itemCode(),
                hold.holdCount(),
                hold.holdAmount(),
                hold.cashDue(),
                hold.catalogAmount(),
                hold.status(),
                hold.orderId());
    }

    @Override
    public void deleteHold(String orderId) {
        jdbc.update("DELETE FROM benefit_hold WHERE order_id = ?", orderId);
    }

    @Override
    public void updateStatus(String orderId, int status) {
        jdbc.update("UPDATE benefit_hold SET status = ? WHERE order_id = ?", status, orderId);
    }

    @Override
    public List<Bucket> lockAccount(String accountId, Instant now) {
        return jdbc.query(
                """
                SELECT c.id, c.valid_to, b.item_code, b.remaining
                FROM benefit_card c
                JOIN benefit_balance b ON b.card_id = c.id
                WHERE c.account_id = ? AND c.status = 1 AND c.valid_to > ?
                FOR UPDATE
                """,
                (rs, rowNum) -> new Bucket(
                        rs.getString("id"),
                        rs.getTimestamp("valid_to").toInstant(),
                        rs.getString("item_code"),
                        rs.getBigDecimal("remaining")),
                accountId,
                Timestamp.from(now));
    }

    @Override
    public boolean take(String cardId, String itemCode, BigDecimal amount) {
        int updated = jdbc.update(
                """
                UPDATE benefit_balance
                SET remaining = remaining - ?
                WHERE card_id = ? AND item_code = ? AND remaining >= ?
                """,
                amount,
                cardId,
                itemCode,
                amount);
        return updated == 1;
    }

    @Override
    public void giveBack(String cardId, String itemCode, BigDecimal amount) {
        jdbc.update(
                "UPDATE benefit_balance SET remaining = remaining + ? WHERE card_id = ? AND item_code = ?",
                amount,
                cardId,
                itemCode);
    }

    @Override
    public List<BenefitLine> lines(String accountId, Instant now) {
        return jdbc.query(
                """
                SELECT c.id, c.product_code, c.valid_to, b.item_code, b.remaining
                FROM benefit_card c
                JOIN benefit_balance b ON b.card_id = c.id
                WHERE c.account_id = ? AND c.status = 1 AND c.valid_to > ?
                ORDER BY c.valid_to, c.id, b.item_code
                """,
                (rs, rowNum) -> new BenefitLine(
                        rs.getString("id"),
                        rs.getString("product_code"),
                        rs.getString("item_code"),
                        rs.getBigDecimal("remaining"),
                        rs.getTimestamp("valid_to").toInstant()),
                accountId,
                Timestamp.from(now));
    }

    private Hold mapHold(ResultSet rs, int rowNum) throws SQLException {
        return new Hold(
                rs.getString("order_id"),
                rs.getString("account_id"),
                rs.getString("card_id"),
                rs.getString("item_code"),
                rs.getInt("hold_count"),
                rs.getBigDecimal("hold_amount"),
                rs.getBigDecimal("cash_due"),
                rs.getBigDecimal("catalog_amount"),
                rs.getInt("status"));
    }
}
