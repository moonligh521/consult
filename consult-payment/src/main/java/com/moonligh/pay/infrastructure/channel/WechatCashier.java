package com.moonligh.pay.infrastructure.channel;

import java.math.RoundingMode;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.pay.PayProperties;
import com.moonligh.pay.domain.Channels;
import com.moonligh.pay.domain.PayCommand;
import com.moonligh.pay.domain.PayException;
import com.wechat.pay.java.core.RSAPublicKeyConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RSAPublicKeyNotificationConfig;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.model.Transaction;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.payments.nativepay.model.Amount;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayRequest;
import com.wechat.pay.java.service.payments.nativepay.model.PrepayResponse;

/** 微信 Native 下单。使用官网 wechatpay-java，验签和解密由 NotificationParser 完成。 */
public final class WechatCashier {

    private static final Logger log = LoggerFactory.getLogger(WechatCashier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PayProperties properties;
    private volatile NativePayService nativePay;
    private volatile NotificationParser parser;

    public WechatCashier(PayProperties properties) {
        this.properties = properties;
    }

    public String prepay(PayCommand command) {
        PayProperties.Wechat wechat = properties.getWechat();
        if (!wechat.ready() || blank(properties.getNotifyBaseUrl())) {
            throw new PayException("PAY_CHANNEL_NOT_READY");
        }
        int fen = command.amountYuan().movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
        PrepayRequest request = new PrepayRequest();
        Amount amount = new Amount();
        amount.setTotal(fen);
        amount.setCurrency("CNY");
        request.setAmount(amount);
        request.setAppid(wechat.getAppId().trim());
        request.setMchid(wechat.getMchId().trim());
        request.setDescription(command.subject());
        request.setNotifyUrl(properties.notifyUrl("wechat"));
        request.setOutTradeNo(Channels.tradeNo("wechat", command.orderId()));
        try {
            PrepayResponse response = service().prepay(request);
            String codeUrl = response == null ? "" : response.getCodeUrl();
            if (codeUrl == null || codeUrl.isBlank()) {
                throw new PayException("PAYMENT_PENDING");
            }
            return codeUrl;
        } catch (PayException error) {
            throw error;
        } catch (RuntimeException error) {
            log.warn("微信下单失败 type={}", error.getClass().getSimpleName());
            throw new PayException("PAYMENT_PENDING");
        }
    }

    /** 验签并解密。失败返回 null。明文里保留 trade_state、out_trade_no 和 amount.total。 */
    public String verifiedPlain(String serial, String body, String signature, String nonce, String timestamp) {
        PayProperties.Wechat wechat = properties.getWechat();
        if (!wechat.ready()) {
            return null;
        }
        try {
            RequestParam request = new RequestParam.Builder()
                    .serialNumber(serial)
                    .nonce(nonce)
                    .signature(signature)
                    .timestamp(timestamp)
                    .body(body)
                    .build();
            Transaction transaction = notifications().parse(request, Transaction.class);
            int total = transaction.getAmount() == null || transaction.getAmount().getTotal() == null
                    ? -1
                    : transaction.getAmount().getTotal();
            String state = transaction.getTradeState() == null ? "" : transaction.getTradeState().name();
            return MAPPER.writeValueAsString(Map.of(
                    "trade_state", state,
                    "out_trade_no", transaction.getOutTradeNo() == null ? "" : transaction.getOutTradeNo(),
                    "amount", Map.of("total", total)));
        } catch (Exception error) {
            log.warn("微信回调验签失败 type={}", error.getClass().getSimpleName());
            return null;
        }
    }

    public static String tradeState(String plain) {
        return text(plain, "trade_state");
    }

    public static String outTradeNo(String plain) {
        return text(plain, "out_trade_no");
    }

    public static int amountFen(String plain) {
        try {
            return MAPPER.readTree(plain).path("amount").path("total").asInt(-1);
        } catch (Exception error) {
            return -1;
        }
    }

    public static Map<String, String> success() {
        return Map.of("code", "SUCCESS", "message", "成功");
    }

    public static Map<String, String> fail() {
        return Map.of("code", "FAIL", "message", "失败");
    }

    private NativePayService service() {
        NativePayService current = nativePay;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (nativePay == null) {
                PayProperties.Wechat wechat = properties.getWechat();
                RSAPublicKeyConfig config = new RSAPublicKeyConfig.Builder()
                        .merchantId(wechat.getMchId().trim())
                        .privateKeyFromPath(wechat.getPrivateKeyPath().trim())
                        .publicKeyFromPath(wechat.getPublicKeyPath().trim())
                        .publicKeyId(wechat.getPublicKeyId().trim())
                        .merchantSerialNumber(wechat.getCertSerialNo().trim())
                        .apiV3Key(wechat.getApiV3Key().trim())
                        .build();
                nativePay = new NativePayService.Builder().config(config).build();
            }
            return nativePay;
        }
    }

    private NotificationParser notifications() {
        NotificationParser current = parser;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (parser == null) {
                PayProperties.Wechat wechat = properties.getWechat();
                RSAPublicKeyNotificationConfig config = new RSAPublicKeyNotificationConfig.Builder()
                        .publicKeyFromPath(wechat.getPublicKeyPath().trim())
                        .publicKeyId(wechat.getPublicKeyId().trim())
                        .apiV3Key(wechat.getApiV3Key().trim())
                        .build();
                parser = new NotificationParser(config);
            }
            return parser;
        }
    }

    private static String text(String plain, String field) {
        try {
            return MAPPER.readTree(plain).path(field).asText("");
        } catch (Exception error) {
            return "";
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
