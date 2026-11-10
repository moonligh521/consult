package com.moonligh.pay;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPayOrderStore implements PayOrderStore {

    private final JdbcTemplate jdbc;

    public JdbcPayOrderStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(PayOrder order) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO pay_order (
                    biz_order_no, channel, amount, subject, notify_url, channel_trade_no, status, payload, created_at
                ) VALUES (?,?,?,?,?,?,?,?,?) AS incoming
                ON DUPLICATE KEY UPDATE
                    channel = IF(pay_order.status = 1, pay_order.channel, incoming.channel),
                    amount = IF(pay_order.status = 1, pay_order.amount, incoming.amount),
                    subject = IF(pay_order.status = 1, pay_order.subject, incoming.subject),
                    notify_url = IF(pay_order.status = 1, pay_order.notify_url, incoming.notify_url),
                    channel_trade_no = IF(pay_order.status = 1, pay_order.channel_trade_no, incoming.channel_trade_no),
                    payload = IF(pay_order.status = 1, pay_order.payload, incoming.payload)
                """,
                order.bizOrderNo(),
                order.channel(),
                order.amount(),
                order.subject(),
                order.notifyUrl(),
                order.channelTradeNo(),
                order.status(),
                order.payload(),
                now);
    }

    @Override
    public PayOrder find(String tradeNo) {
        if (tradeNo == null || tradeNo.isBlank()) {
            return null;
        }
        List<PayOrder> rows = jdbc.query("""
                SELECT biz_order_no, channel, amount, subject, notify_url, channel_trade_no, status, payload
                FROM pay_order
                WHERE biz_order_no = ? OR channel_trade_no = ? OR REPLACE(biz_order_no, '-', '') = ?
                LIMIT 1
                """, JdbcPayOrderStore::map, tradeNo, tradeNo, tradeNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void markPaid(String bizOrderNo) {
        jdbc.update(
                "UPDATE pay_order SET status = 1, paid_at = COALESCE(paid_at, ?) WHERE biz_order_no = ? AND status = 0",
                Timestamp.from(Instant.now()),
                bizOrderNo);
    }

    private static PayOrder map(ResultSet row, int index) throws SQLException {
        return new PayOrder(
                row.getString("biz_order_no"),
                row.getString("channel"),
                row.getBigDecimal("amount"),
                row.getString("subject"),
                row.getString("notify_url"),
                row.getString("channel_trade_no"),
                row.getInt("status"),
                row.getString("payload"));
    }
}
