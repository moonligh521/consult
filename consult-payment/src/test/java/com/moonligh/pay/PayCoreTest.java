package com.moonligh.pay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PayCoreTest {

    @Test
    void wechatWithoutMerchantIsNotReady() {
        PaymentService payments = new PaymentService(new PayProperties(), new MemoryPayOrderStore(), new BusinessNotifier(new PayProperties()));
        PayException error = assertThrows(PayException.class, () -> payments.create(new PayRequest(
                "order-1", new BigDecimal("3.00"), "在线问诊", "wechat", "http://127.0.0.1/paid")));
        assertEquals("PAY_CHANNEL_NOT_READY", error.code());
    }

    @Test
    void bankParameterStringOmitsSignatureAndSortsKeys() {
        String covered = BankCashier.cover(Map.of("b", "2", "a", "1", "signature", "skip", "empty", ""));
        assertEquals("a=1&b=2", covered);
    }

    @Test
    void businessJsonCodeMustBe200() {
        assertTrue(BusinessNotifier.accepted(""));
        assertTrue(BusinessNotifier.accepted("{\"code\":200}"));
        assertFalse(BusinessNotifier.accepted("{\"code\":1114,\"message\":\"待支付\"}"));
    }

    @Test
    void wechatTradeNoDropsHyphens() {
        assertEquals("abc", Channels.tradeNo("wechat", "a-b-c"));
        assertEquals("a-b-c", Channels.tradeNo("alipay", "a-b-c"));
        assertEquals("bank", Channels.normalize("unionpay"));
    }

    private static final class MemoryPayOrderStore implements PayOrderStore {
        @Override
        public void save(PayOrder order) {
        }

        @Override
        public PayOrder find(String tradeNo) {
            return null;
        }

        @Override
        public void markPaid(String bizOrderNo) {
        }
    }
}
