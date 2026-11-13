package com.moonligh.consult.agent.infrastructure.benefit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.res.BenefitItemRes;
import com.moonligh.consult.agent.config.BenefitProperties;
import com.moonligh.consult.agent.domain.benefit.BenefitGateway;
import com.moonligh.consult.agent.domain.benefit.BenefitQuote;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/** 把预占、确认、释放转到独立权益服务。令牌为空时不下单不查额度。 */
@Component
@EnableConfigurationProperties(BenefitProperties.class)
public class HttpBenefitGateway implements BenefitGateway {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BenefitProperties properties;

    public HttpBenefitGateway(BenefitProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return properties != null && properties.ready();
    }

    @Override
    public BenefitQuote reserve(String accountId, String orderId, int packageCode, BigDecimal catalogPay) {
        JsonNode node = post("/api/benefit/holds", new BenefitHoldReq(
                accountId,
                orderId,
                packageCode,
                catalogPay.setScale(2, RoundingMode.HALF_UP)));
        JsonNode card = node.get("cardId");
        return new BenefitQuote(
                card == null || card.isNull() ? null : card.asText(),
                money(node.get("benefitAmount")),
                money(node.get("cashDue")));
    }

    @Override
    public void confirm(String orderId) {
        post("/api/benefit/holds/" + orderId + "/confirm");
    }

    @Override
    public void release(String orderId) {
        post("/api/benefit/holds/" + orderId + "/release");
    }

    @Override
    public void restore(String orderId) {
        post("/api/benefit/holds/" + orderId + "/restore");
    }

    @Override
    public List<BenefitItemRes> account(String accountId) {
        if (!enabled()) {
            return List.of();
        }
        try {
            String body = client().get()
                    .uri(root() + "/api/benefit/accounts/" + accountId)
                    .header("X-Benefit-Token", properties.getToken().trim())
                    .retrieve()
                    .body(String.class);
            JsonNode node = body == null || body.isBlank() ? null : MAPPER.readTree(body);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<BenefitItemRes> lines = new ArrayList<>();
            for (JsonNode line : node) {
                lines.add(new BenefitItemRes(
                        text(line, "cardId"),
                        text(line, "productCode"),
                        text(line, "itemCode"),
                        money(line.get("remaining")),
                        Instant.parse(text(line, "validTo"))));
            }
            return lines;
        } catch (RestClientException error) {
            throw new BusinessException(ErrorCode.BENEFIT_UNAVAILABLE);
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.BENEFIT_UNAVAILABLE);
        }
    }

    private JsonNode post(String path) {
        return post(path, null);
    }

    private JsonNode post(String path, BenefitHoldReq body) {
        if (!enabled()) {
            throw new BusinessException(ErrorCode.BENEFIT_UNAVAILABLE);
        }
        try {
            var request = client().post()
                    .uri(root() + path)
                    .header("X-Benefit-Token", properties.getToken().trim())
                    .contentType(MediaType.APPLICATION_JSON);
            String raw = (body == null ? request : request.body(body))
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) {
                return MAPPER.createObjectNode();
            }
            return MAPPER.readTree(raw);
        } catch (RestClientException error) {
            throw new BusinessException(ErrorCode.BENEFIT_UNAVAILABLE);
        } catch (Exception error) {
            throw new BusinessException(ErrorCode.BENEFIT_UNAVAILABLE);
        }
    }

    private RestClient client() {
        return RestClient.create();
    }

    private String root() {
        String base = properties.getBaseUrl().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static BigDecimal money(JsonNode node) {
        if (node == null || node.isNull()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return node.decimalValue().setScale(2, RoundingMode.HALF_UP);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
