package com.moonligh.consult.agent.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.req.BindPatientReq;
import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.req.ConfirmReq;
import com.moonligh.consult.agent.api.req.ConsultSessionReq;
import com.moonligh.consult.agent.api.req.EndConsultReq;
import com.moonligh.consult.agent.api.req.MessageReq;
import com.moonligh.consult.agent.api.req.OrderReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.api.res.PayPackageRes;
import com.moonligh.consult.security.AccountIds;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.Result;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 咨询 HTTP 入口。这里不写规则，只把请求转给 ConsultationService。
 */
@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    private final ConsultationService service;

    public ConsultationController(ConsultationService service) {
        this.service = service;
    }

    /**
     * 患者发一句话。consultationId 为空则新开一轮。
     */
    @PostMapping("/messages")
    public Result<ConsultationRes> acceptMessage(@RequestBody MessageReq request) {
        // 请求体为空时按空文本处理，由服务返回提示。
        if (request == null) {
            return Result.ok(service.acceptMessage(null, null, null, ""));
        }
        return Result.ok(service.acceptMessage(
                request.consultationId(), request.messageId(), request.serviceType(), request.text()));
    }

    /**
     * 修改已生成的咨询卡片。
     */
    @PatchMapping("/card")
    public Result<ConsultationRes> updateCard(@RequestBody CardPatchReq request) {
        return Result.ok(service.updateCard(request));
    }

    /**
     * 确认卡片。确认后仍接受病情变化消息，先做安全重评。
     */
    @PostMapping("/confirmation")
    public Result<ConsultationRes> confirm(@RequestBody ConfirmReq request) {
        if (request == null) {
            return Result.ok(service.confirm(null));
        }
        return Result.ok(service.confirm(request.consultationId()));
    }

    /**
     * 卡片确认后的四档咨询。不依赖某一轮会话。
     */
    @GetMapping("/packages")
    public Result<List<PayPackageRes>> packages() {
        return Result.ok(service.packages());
    }

    /**
     * 把当前账号的一位就诊人绑到本轮咨询。
     */
    @PostMapping("/patient")
    public Result<ConsultationRes> bindPatient(@RequestBody BindPatientReq request, HttpServletRequest http) {
        if (request == null) {
            return Result.ok(service.bindPatient(null, null, AccountIds.of(http)));
        }
        return Result.ok(service.bindPatient(request.consultationId(), request.patientId(), AccountIds.of(http)));
    }

    /**
     * 选择档位并记下演示订单。不走真实支付。
     */
    @PostMapping("/orders")
    public Result<ConsultationRes> placeOrder(@RequestBody OrderReq request) {
        if (request == null) {
            return Result.ok(service.placeOrder(null, null));
        }
        return Result.ok(service.placeOrder(request.consultationId(), request.packageCode(), request.consultMinutes()));
    }

    /**
     * 结束问诊并释放医生占坑。超时和手动结束都走这里。
     */
    @PostMapping("/end")
    public Result<ConsultationRes> endConsult(@RequestBody EndConsultReq request) {
        if (request == null) {
            return Result.ok(service.endConsult(null, null));
        }
        return Result.ok(service.endConsult(request.consultationId(), request.reason()));
    }

    /**
     * 问诊中改时长。从当前时刻重新计时。
     */
    @PostMapping("/session")
    public Result<ConsultationRes> setSession(@RequestBody ConsultSessionReq request) {
        if (request == null) {
            return Result.ok(service.setConsultMinutes(null, null));
        }
        return Result.ok(service.setConsultMinutes(request.consultationId(), request.consultMinutes()));
    }
}
