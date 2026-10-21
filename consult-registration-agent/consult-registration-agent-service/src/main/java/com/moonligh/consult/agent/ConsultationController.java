package com.moonligh.consult.agent;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.req.CardPatchReq;
import com.moonligh.consult.agent.api.req.ConfirmReq;
import com.moonligh.consult.agent.api.req.MessageReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.framework.Result;

/**
 * 咨询接口。编号放在请求体，路径上不带参数。返回统一的 {@link Result}。
 */
@RestController
@RequestMapping("/api/consultations")
public class ConsultationController {

    private final ConsultationService service;

    public ConsultationController(ConsultationService service) {
        this.service = service;
    }

    @PostMapping("/messages")
    public Result<ConsultationRes> acceptMessage(@RequestBody MessageReq request) {
        if (request == null) {
            return Result.ok(service.acceptMessage(null, ""));
        }
        return Result.ok(service.acceptMessage(request.consultationId(), request.text()));
    }

    @PatchMapping("/card")
    public Result<ConsultationRes> updateCard(@RequestBody CardPatchReq request) {
        return Result.ok(service.updateCard(request));
    }

    @PostMapping("/confirmation")
    public Result<ConsultationRes> confirm(@RequestBody ConfirmReq request) {
        if (request == null) {
            return Result.ok(service.confirm(null));
        }
        return Result.ok(service.confirm(request.consultationId()));
    }
}
