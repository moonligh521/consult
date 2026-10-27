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
import com.moonligh.consult.agent.domain.safety.ModelStatus;
import com.moonligh.consult.agent.domain.safety.RuleSafetyEngine;
import com.moonligh.consult.agent.domain.safety.SignalExtraction;
import com.moonligh.consult.agent.domain.triage.ComplaintReply;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/**
 * 依赖失败走写死的兜底。只打测试桩，不在运行中的服务上断网。
 */
class FaultEvalTest {

    @Test
    void faults() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(safetyModel("F1-TIMEOUT", SignalExtraction.timeout(), 7, ErrorCode.SAFE_FALLBACK_STOP));
        rows.add(safetyModel("F1-UNAVAILABLE", SignalExtraction.unavailable(), 7, ErrorCode.SAFE_FALLBACK_STOP));
        rows.add(safetyModel("F1-INVALID", SignalExtraction.invalid(), 7, ErrorCode.SAFE_FALLBACK_STOP));
        rows.add(skipModel());
        rows.add(complaintUnavailable());
        rows.add(engineThrows());
        rows.add(duplicateOrder());
        rows.add(releaseOnce());
        rows.add(noDoctor());
        rows.add(closed());
        rows.add(noRecallOnPay());
        EvalSupport.replaceSuite("fault", rows);
        List<String> csvIds = EvalSupport.readCsv("fault-cases.csv").stream().map(row -> row.get("case_id")).toList();
        List<String> got = rows.stream().map(row -> String.valueOf(row.get("case_id"))).toList();
        assertEquals(csvIds, got);
        for (Map<String, Object> row : rows) {
            assertEquals(Boolean.TRUE, row.get("pass"), String.valueOf(row.get("case_id")) + " " + row.get("detail"));
        }
    }

    private static Map<String, Object> safetyModel(String caseId, SignalExtraction extraction, int status, ErrorCode error) {
        AtomicInteger calls = new AtomicInteger();
        ConsultationService service = EvalSupport.service((transcript, demographics) -> {
            calls.incrementAndGet();
            return extraction;
        }, new RuleSafetyEngine(), null, null, null);
        ConsultationRes view = service.acceptMessage(null, "胸口闷");
        int code = EvalSupport.errorCode(() -> service.placeOrder(view.consultationId(), 1));
        return finish(caseId, view, code, calls.get() > 0, status, error.getCode(), true);
    }

    private static Map<String, Object> skipModel() {
        AtomicInteger calls = new AtomicInteger();
        ConsultationService service = EvalSupport.service((transcript, demographics) -> {
            calls.incrementAndGet();
            return SignalExtraction.disabled();
        }, new RuleSafetyEngine(), null, null, null);
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        return finish("F2-SKIP", view, 0, calls.get() > 0, 4, 0, false);
    }

    private static Map<String, Object> complaintUnavailable() {
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null,
                (description, age, gender, allowTerms, candidates) -> ComplaintReply.failed(ModelStatus.UNAVAILABLE, 0, null, null),
                null);
        ConsultationRes view = service.acceptMessage(null, "女，32岁，头晕乏力一周");
        String specialty = view.card() == null ? "" : view.card().specialty();
        Map<String, Object> row = finish("F3-UNAVAILABLE", view, 0, false, view.status().getCode(), 0, false);
        boolean pass = "全科".equals(specialty) && (view.prompt() == null || !view.prompt().contains("火星科"));
        row.put("pass", pass);
        row.put("detail", pass ? "" : "二级科=" + specialty);
        return row;
    }

    private static Map<String, Object> engineThrows() {
        RuleSafetyEngine engine = new RuleSafetyEngine() {
            @Override
            public com.moonligh.consult.agent.domain.safety.SafetyAssessment evaluate(
                    java.util.List<com.moonligh.consult.agent.domain.safety.ConsultationMessage> transcript,
                    com.moonligh.consult.agent.domain.safety.Demographics demographics,
                    com.moonligh.consult.agent.domain.safety.SafetyCatalog catalog,
                    boolean includeDraftInDecision) {
                throw new IllegalStateException("rule engine down");
            }
        };
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), engine, null, null, null);
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        int code = EvalSupport.errorCode(() -> service.placeOrder(view.consultationId(), 1));
        return finish("F4-ENGINE", view, code, false, 7, ErrorCode.SAFE_FALLBACK_STOP.getCode(), null);
    }

    private static Map<String, Object> duplicateOrder() {
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null, null, null);
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        service.confirm(view.consultationId());
        ConsultationRes paid = service.placeOrder(view.consultationId(), 1);
        int again = EvalSupport.errorCode(() -> service.placeOrder(view.consultationId(), 4));
        Map<String, Object> row = finish("F5-DUP", paid, again, false, 5, ErrorCode.ALREADY_ORDERED.getCode(), null);
        row.put("order_count", 1);
        row.put("pass", paid.order() != null && again == ErrorCode.ALREADY_ORDERED.getCode());
        return row;
    }

    private static Map<String, Object> releaseOnce() {
        AtomicInteger releases = new AtomicInteger();
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null, null,
                new com.moonligh.consult.agent.domain.DoctorAssigner() {
                    @Override
                    public com.moonligh.consult.agent.api.res.AssignedDoctorRes assign(String consultationId, String orderId,
                            com.moonligh.consult.agent.api.res.ConsultationCardRes card, java.util.List<String> canonicalTerms) {
                        return new com.moonligh.consult.agent.api.res.AssignedDoctorRes(
                                "doc-1", "周宁", "主治医师", "互联网医院", 1, card.specialty(), "干咳", "干咳", "sch-1", "SKILL_TEXT");
                    }

                    @Override
                    public void release(String orderId, String reason) {
                        releases.incrementAndGet();
                    }
                });
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        service.confirm(view.consultationId());
        ConsultationRes paid = service.placeOrder(view.consultationId(), 1);
        service.endConsult(view.consultationId(), "ENDED");
        service.endConsult(view.consultationId(), "ENDED");
        Map<String, Object> row = EvalSupport.evidence("F5-RELEASE", paid, 0, false);
        boolean pass = releases.get() == 1;
        row.put("pass", pass);
        row.put("detail", pass ? "" : "释放次数 " + releases.get());
        return row;
    }

    private static Map<String, Object> noDoctor() {
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null, null,
                (consultationId, orderId, card, terms) -> {
                    throw new BusinessException(ErrorCode.DOCTOR_UNAVAILABLE);
                });
        ConsultationRes ready = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        ConsultationRes view = service.confirm(ready.consultationId());
        ConsultationRes paid = service.placeOrder(view.consultationId(), 1);
        Map<String, Object> row = finish("F6-NONE", paid, 0, false, 5, 0, null);
        boolean queued = paid.order() != null && paid.order().doctor() == null;
        if (!queued) {
            row.put("pass", false);
            row.put("detail", "应保留未派医生的订单");
        }
        return row;
    }

    private static Map<String, Object> closed() {
        InquiryProperties inquiry = new InquiryProperties();
        inquiry.setClosed(true);
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), inquiry, null, null);
        ConsultationRes view = service.acceptMessage(null, "女，32岁，干咳半年，运动后加重");
        int code = EvalSupport.errorCode(() -> service.confirm(view.consultationId()));
        return finish("F7-CLOSED", view, code, false, 4, ErrorCode.INQUIRY_CLOSED.getCode(), null);
    }

    private static Map<String, Object> noRecallOnPay() {
        AtomicInteger calls = new AtomicInteger();
        ConsultationService service = EvalSupport.service(new DisabledSignalExtractor(), new RuleSafetyEngine(), null,
                (description, age, gender, allowTerms, candidates) -> {
                    calls.incrementAndGet();
                    return ComplaintReply.failed(ModelStatus.UNAVAILABLE, 0, null, null);
                }, null);
        ConsultationRes view = service.acceptMessage(null, "男，50岁，高血压，头晕三天");
        int beforePay = calls.get();
        service.confirm(view.consultationId());
        ConsultationRes paid = service.placeOrder(view.consultationId(), 1);
        Map<String, Object> row = EvalSupport.evidence("F8-NO-RECALL", paid, 0, calls.get() > beforePay);
        boolean pass = calls.get() == beforePay && paid.order() != null;
        row.put("pass", pass);
        row.put("detail", pass ? "" : "下单后又调了模型，调用 " + calls.get());
        return row;
    }

    private static Map<String, Object> finish(String caseId, ConsultationRes view, int error, boolean modelCalled,
            int expectStatus, int expectError, Boolean expectCalled) {
        Map<String, Object> row = EvalSupport.evidence(caseId, view, error, modelCalled);
        StringBuilder detail = new StringBuilder();
        if (view.status().getCode() != expectStatus) {
            detail.append("状态 ").append(view.status().getCode()).append(" 期望 ").append(expectStatus).append("; ");
        }
        if (expectError != 0 && error != expectError) {
            detail.append("错误码 ").append(error).append(" 期望 ").append(expectError).append("; ");
        }
        if (expectCalled != null && modelCalled != expectCalled) {
            detail.append("model_called=").append(modelCalled);
        }
        row.put("pass", detail.isEmpty());
        row.put("detail", detail.toString());
        return row;
    }
}
