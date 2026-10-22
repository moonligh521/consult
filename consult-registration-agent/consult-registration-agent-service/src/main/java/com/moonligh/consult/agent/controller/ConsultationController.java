package com.moonligh.consult.agent.controller;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.req.ConfirmReq;
import com.moonligh.consult.agent.api.req.MessageReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.Result;

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
}
