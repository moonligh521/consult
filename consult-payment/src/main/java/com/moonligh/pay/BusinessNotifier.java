package com.moonligh.pay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 验签通过后通知业务方。业务方返回的 JSON 里 code 不是 200，就让渠道重试。 */
@Component
public class BusinessNotifier {

    private static final Logger log = LoggerFactory.getLogger(BusinessNotifier.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PayProperties properties;

    public BusinessNotifier(PayProperties properties) {
        this.properties = properties;
    }

    public boolean notify(PayOrder order) {
        try {
            String body = RestClient.create().post()
                    .uri(order.notifyUrl())
                    .header("X-Pay-Token", properties.getToken() == null ? "" : properties.getToken().trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaidNotice(order.bizOrderNo(), order.amount().toPlainString(), order.channel()))
                    .retrieve()
                    .body(String.class);
            return accepted(body);
        } catch (RuntimeException error) {
            log.warn("通知业务方失败 order={} type={}", order.bizOrderNo(), error.getClass().getSimpleName());
            return false;
        }
    }

    static boolean accepted(String body) {
        if (body == null || body.isBlank()) {
            return true;
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            if (node.has("code")) {
                return node.get("code").asInt(-1) == 200;
            }
            return true;
        } catch (Exception error) {
            return true;
        }
    }
}
