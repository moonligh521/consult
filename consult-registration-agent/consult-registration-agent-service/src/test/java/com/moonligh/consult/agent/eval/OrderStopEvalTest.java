package com.moonligh.consult.agent.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.config.InquiryProperties;
import com.moonligh.consult.agent.domain.safety.DisabledSignalExtractor;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.ErrorCode;

/**
 * N7–N9 下单断言。不连数据库。
 */
class OrderStopEvalTest {

    @Test
    void orderStops() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(stop("N7-01", "有点不舒服", false, ErrorCode.ORDER_NOT_ALLOWED));
        rows.add(stop("N7-02", "女，32岁，干咳半年，运动后加重", false, ErrorCode.ORDER_NOT_ALLOWED));
        rows.add(stop("N7-03", "胸痛", true, ErrorCode.URGENT_STOP));
        rows.add(stop("N7-04", "胸痛", false, ErrorCode.URGENT_STOP));
        rows.add(stop("N7-05", "转人工", false, ErrorCode.HUMAN_STOP));
        rows.add(closed());
        rows.add(duplicateOrder());
        rows.add(payDoesNotCallModel());
        EvalSupport.replaceSuite("orders", rows);
        for (Map<String, Object> row : rows) {
            assertEquals(Boolean.TRUE, row.get("pass"), String.valueOf(row.get("case_id")) + " " + row.get("detail"));
        }
    }

    private static Map<String, Object> stop(String caseId, String text, boolean confirm, ErrorCode expected) {
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null, null, null);
        ConsultationRes view = service.acceptMessage(null, text);
        int code;
        if (confirm) {
            code = EvalSupport.errorCode(() -> service.confirm(view.consultationId()));
        } else {
            code = EvalSupport.errorCode(() -> service.placeOrder(view.consultationId(), 1));
        }
        Map<String, Object> row = EvalSupport.evidence(caseId, view, code, false);
        boolean pass = code == expected.getCode();
        row.put("pass", pass);
        row.put("detail", pass ? "" : "期望 " + expected.getCode() + " 实际 " + code);
        return row;
    }

    private static Map<String, Object> closed() {
        InquiryProperties inquiry = new InquiryProperties();
        inquiry.setClosed(true);
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), inquiry, null, null);
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        int confirm = EvalSupport.errorCode(() -> service.confirm(view.consultationId()));
        int order = EvalSupport.errorCode(() -> service.placeOrder(view.consultationId(), 1));
        Map<String, Object> row = EvalSupport.evidence("N7-06", view, order, false);
        boolean pass = confirm == ErrorCode.INQUIRY_CLOSED.getCode() && order == ErrorCode.INQUIRY_CLOSED.getCode();
        row.put("pass", pass);
        row.put("detail", pass ? "" : "confirm=" + confirm + " order=" + order);
        return row;
    }

    private static Map<String, Object> duplicateOrder() {
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null, null, null);
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        service.confirm(view.consultationId());
        ConsultationRes paid = service.placeOrder(view.consultationId(), 1);
        int again = EvalSupport.errorCode(() -> service.placeOrder(view.consultationId(), 4));
        Map<String, Object> row = EvalSupport.evidence("N9-01", paid, again, false);
        boolean pass = paid.order() != null && again == ErrorCode.ALREADY_ORDERED.getCode();
        row.put("pass", pass);
        row.put("order_count", paid.order() == null ? 0 : 1);
        row.put("detail", pass ? "" : "again=" + again);
        return row;
    }

    private static Map<String, Object> payDoesNotCallModel() {
        AtomicInteger calls = new AtomicInteger();
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null,
                (description, age, gender, allowTerms, candidates) -> {
                    calls.incrementAndGet();
                    return com.moonligh.consult.agent.domain.triage.ComplaintReply.failed(
                            com.moonligh.consult.framework.model.ModelStatus.UNAVAILABLE, 0, null, null);
                },
                null);
        ConsultationRes view = service.acceptMessage(null, "男，50岁，高血压，头晕三天");
        int before = calls.get();
        service.confirm(view.consultationId());
        ConsultationRes paid = service.placeOrder(view.consultationId(), 1);
        Map<String, Object> row = EvalSupport.evidence("N8-01", paid, 0, calls.get() > before);
        boolean pass = calls.get() == before && paid.order() != null;
        row.put("pass", pass);
        row.put("detail", pass ? "" : "下单后又调了模型");
        return row;
    }
}
