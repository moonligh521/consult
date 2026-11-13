package com.moonligh.consult.agent.infrastructure.pay;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.config.PayProperties;
import com.moonligh.consult.agent.domain.pay.PayCommand;
import com.moonligh.consult.agent.domain.pay.PaymentCashier;
import com.moonligh.consult.agent.domain.pay.PaymentCreateReq;
import com.moonligh.consult.agent.domain.pay.PaymentTicket;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 空渠道和 demo 仍直接记已支付。支付宝、微信、银行转到独立支付服务。
 */
@Component
@EnableConfigurationProperties(PayProperties.class)
public class ChannelPaymentCashier implements PaymentCashier {

    private static final Logger log = LoggerFactory.getLogger(ChannelPaymentCashier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PayProperties properties;
    private final DemoPaymentCashier demo = new DemoPaymentCashier();

    public ChannelPaymentCashier(PayProperties properties) {
        this.properties = properties;
    }

    @Override
    public PaymentTicket collect(PayCommand command) {
        return switch (normalize(command == null ? null : command.channel())) {
            case "alipay", "wechat", "bank" -> remote(command, normalize(command.channel()));
            case "demo" -> demo.collect(command);
            default -> throw new BusinessException(ErrorCode.PARAM_INVALID);
        };
    }

    public static String normalize(String channel) {
        if (channel == null || channel.isBlank() || "demo".equalsIgnoreCase(channel.trim())) {
            return "demo";
        }
        String value = channel.trim().toLowerCase();
        return "unionpay".equals(value) ? "bank" : value;
    }

    public static boolean real(String channel) {
        String value = normalize(channel);
        return "alipay".equals(value) || "wechat".equals(value) || "bank".equals(value);
    }

    private PaymentTicket remote(PayCommand command, String channel) {
        if (properties == null || !properties.ready()) {
            throw new BusinessException(ErrorCode.PAY_CHANNEL_NOT_READY);
        }
        String base = properties.getBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        try {
            String body = RestClient.create().post()
                    .uri(base + "/api/payments")
                    .header("X-Pay-Token", properties.getToken().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentCreateReq(
                            command.orderId(),
                            command.amountYuan().setScale(2, RoundingMode.HALF_UP),
                            command.subject(),
                            channel,
                            properties.getCallbackUrl().trim()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        if (text.contains("PAY_CHANNEL_NOT_READY")) {
                            throw new BusinessException(ErrorCode.PAY_CHANNEL_NOT_READY);
                        }
                        throw new BusinessException(ErrorCode.PAYMENT_PENDING);
                    })
                    .body(String.class);
            JsonNode node = MAPPER.readTree(body);
            return new PaymentTicket(
                    node.path("paid").asBoolean(false),
                    node.path("channel").asText(channel),
                    node.path("payload").asText(""));
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            if (error.getCause() instanceof BusinessException business) {
                throw business;
            }
            log.warn("支付服务下单失败 type={}", error.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.PAYMENT_PENDING);
        } catch (Exception error) {
            log.warn("支付服务下单失败 type={}", error.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.PAYMENT_PENDING);
        }
    }
}
