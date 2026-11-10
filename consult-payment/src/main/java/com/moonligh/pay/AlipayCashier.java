package com.moonligh.pay;

import java.math.RoundingMode;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradeWapPayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;

/** 支付宝手机网站支付。使用官网 alipay-sdk-java 的 pageExecute，这里不写已支付。 */
public final class AlipayCashier {

    private static final Logger log = LoggerFactory.getLogger(AlipayCashier.class);

    private final PayProperties properties;

    public AlipayCashier(PayProperties properties) {
        this.properties = properties;
    }

    public String prepay(PayCommand command) {
        PayProperties.Alipay alipay = properties.getAlipay();
        if (!alipay.ready() || blank(properties.getNotifyBaseUrl())) {
            throw new PayException("PAY_CHANNEL_NOT_READY");
        }
        AlipayClient client = new DefaultAlipayClient(
                alipay.getServerUrl().trim(),
                alipay.getAppId().trim(),
                alipay.getPrivateKey().trim(),
                "json",
                "UTF-8",
                alipay.getAlipayPublicKey().trim(),
                "RSA2");
        AlipayTradeWapPayModel model = new AlipayTradeWapPayModel();
        model.setOutTradeNo(command.orderId());
        model.setTotalAmount(command.amountYuan().setScale(2, RoundingMode.HALF_UP).toPlainString());
        model.setSubject(command.subject());
        model.setProductCode("QUICK_WAP_PAY");
        model.setTimeoutExpress("30m");
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
        request.setNotifyUrl(properties.notifyUrl("alipay"));
        request.setReturnUrl(properties.getReturnUrl() == null ? "" : properties.getReturnUrl().trim());
        request.setBizModel(model);
        try {
            AlipayTradeWapPayResponse response = client.pageExecute(request);
            String form = response == null ? "" : response.getBody();
            if (form == null || form.isBlank()) {
                throw new PayException("PAYMENT_PENDING");
            }
            return form;
        } catch (PayException error) {
            throw error;
        } catch (AlipayApiException error) {
            log.warn("支付宝下单失败 type={}", error.getClass().getSimpleName());
            throw new PayException("PAYMENT_PENDING");
        }
    }

    public boolean verify(Map<String, String> params) {
        try {
            return AlipaySignature.rsaCheckV1(
                    params,
                    properties.getAlipay().getAlipayPublicKey(),
                    "UTF-8",
                    "RSA2");
        } catch (AlipayApiException error) {
            log.warn("支付宝回调验签失败 type={}", error.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
