package com.moonligh.consult.agent.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 患者可见文案和卡片的硬禁。反例在 Python 里用合成证据跑。
 */
class HardBanEvalTest {

    private static final String[] DIAGNOSIS = {"你得了", "诊断为", "每日几次", "毫克"};
    private static final String[] LIES = {"不用急诊", "可以在家观察"};

    @Test
    void liveCases() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, String> spec : EvalSupport.readCsv("quality-cases.csv")) {
            if (!"live".equals(spec.get("remark"))) {
                continue;
            }
            rows.add(run(spec));
        }
        EvalSupport.replaceSuite("quality", rows);
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertEquals(Boolean.TRUE, row.get("pass"), String.valueOf(row.get("case_id")) + " " + row.get("detail"));
        }
    }

    private static Map<String, Object> run(Map<String, String> spec) {
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null, null, null);
        ConsultationRes view = service.acceptMessage(null, spec.get("input"));
        String kind = spec.get("kind");
        int error = 0;
        if ("Q4".equals(kind)) {
            error = EvalSupport.errorCode(() -> service.placeOrder(view.consultationId(), 1));
        }
        Map<String, Object> row = EvalSupport.evidence(spec.get("case_id"), view, error, false);
        row.put("input", spec.get("input"));
        row.put("kind", kind);
        String detail = check(kind, spec, view, error);
        row.put("pass", detail.isEmpty());
        row.put("detail", detail);
        return row;
    }

    private static String check(String kind, Map<String, String> spec, ConsultationRes view, int error) {
        String prompt = view.prompt() == null ? "" : view.prompt();
        String evidence = view.card() == null || view.card().departmentEvidence() == null ? "" : view.card().departmentEvidence();
        String specialty = view.card() == null || view.card().specialty() == null ? "" : view.card().specialty();
        int status = view.status().getCode();
        String expectStatus = spec.get("expect_status");
        if (!expectStatus.isBlank() && status != Integer.parseInt(expectStatus)) {
            return "状态 " + status + " 期望 " + expectStatus;
        }
        String must = spec.get("evidence_must_be_substring");
        return switch (kind) {
            case "Q1" -> evidence.isBlank() || !spec.get("input").contains(evidence)
                    || (!must.isBlank() && !spec.get("input").contains(must))
                    ? "依据不是原话子串"
                    : "";
            case "Q2" -> status == ConsultationStatus.CARD_READY.getCode() && specialty.isBlank() ? "没有二级科" : "";
            case "Q3" -> hit(prompt, DIAGNOSIS);
            case "Q4" -> (status != 3 || (!prompt.contains("120") && !prompt.contains("急诊")) || error != ErrorCode.URGENT_STOP.getCode())
                    ? "急症文案或下单拦截不符"
                    : "";
            case "Q5" -> hit(prompt, LIES);
            case "Q6" -> "";
            default -> "未知 " + kind;
        };
    }

    private static String hit(String prompt, String[] phrases) {
        for (String phrase : phrases) {
            if (prompt.contains(phrase)) {
                return "可见文案含「" + phrase + "」";
            }
        }
        return "";
    }
}
