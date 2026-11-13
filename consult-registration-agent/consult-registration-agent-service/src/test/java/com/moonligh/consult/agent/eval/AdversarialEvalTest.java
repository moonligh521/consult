package com.moonligh.consult.agent.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.service.ConsultationService;

/**
 * 对抗句只断言状态、错误码和患者可见文案，不让模型判断攻击是否成功。
 */
class AdversarialEvalTest {

    @Test
    void attacks() throws Exception {
        List<Map<String, String>> specs = EvalSupport.readCsv("adversarial-cases.csv");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, String> spec : specs) {
            rows.add(run(spec));
        }
        EvalSupport.replaceSuite("adversarial", rows);
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertEquals(Boolean.TRUE, row.get("pass"), String.valueOf(row.get("case_id")) + " " + row.get("detail"));
        }
    }

    private static Map<String, Object> run(Map<String, String> spec) {
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null, null, null);
        String[] turns = spec.get("turns").split(" \\|\\| ");
        ConsultationRes view = null;
        for (int i = 0; i < turns.length; i++) {
            if (i == turns.length - 1 && spec.get("remark").contains("confirm_before_last") && view != null) {
                service.confirm(view.consultationId());
            }
            view = service.acceptMessage(view == null ? null : view.consultationId(), turns[i].trim());
        }
        int error = 0;
        String expectError = spec.get("expect_error").trim();
        if (!expectError.isEmpty() && view != null) {
            ConsultationRes current = view;
            error = EvalSupport.errorCode(() -> service.placeOrder(current.consultationId(), 1));
        }
        Map<String, Object> row = EvalSupport.evidence(spec.get("case_id"), view, error, false);
        StringBuilder detail = new StringBuilder();
        String expectStatus = spec.get("expect_status").trim();
        if (!expectStatus.isEmpty() && view.status().getCode() != Integer.parseInt(expectStatus)) {
            detail.append("状态 ").append(view.status().getCode()).append(" 期望 ").append(expectStatus).append("; ");
        }
        if (!expectError.isEmpty() && error != Integer.parseInt(expectError)) {
            detail.append("错误码 ").append(error).append(" 期望 ").append(expectError).append("; ");
        }
        if (EvalSupport.containsAny(view.prompt(), spec.get("forbid_prompt_contains"))) {
            detail.append("可见文案含禁止子串; ");
        }
        String expectOrders = spec.get("expect_order_count").trim();
        int orders = view.order() == null ? 0 : 1;
        if (!expectOrders.isEmpty() && orders != Integer.parseInt(expectOrders)) {
            detail.append("订单数 ").append(orders);
        }
        row.put("pass", detail.isEmpty());
        row.put("detail", detail.toString());
        return row;
    }
}
