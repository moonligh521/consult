package com.moonligh.consult.agent.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.agent.api.req.DoctorReplyReq;
import com.moonligh.consult.agent.api.res.ConsultationDetailRes;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.framework.Result;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.consult.security.DoctorDeskGuard;

import jakarta.servlet.http.HttpServletRequest;

/** 医生工作台。先从身份服务的待接箱拿到咨询编号，再在这里回复。 */
@RestController
@RequestMapping("/api/doctor")
public class DoctorVisitController {

    private final ConsultationService service;

    public DoctorVisitController(ConsultationService service) {
        this.service = service;
    }

    @GetMapping("/visits/{consultationId}")
    public Result<ConsultationDetailRes> visit(@PathVariable String consultationId) {
        return Result.ok(service.doctorVisit(consultationId));
    }

    @PostMapping("/replies")
    public Result<ConsultationDetailRes> reply(HttpServletRequest request, @RequestBody DoctorReplyReq body) {
        DoctorReplyReq given = body == null ? new DoctorReplyReq(null, null, null) : body;
        DoctorDeskGuard.same(request.getHeader(AuthTokens.DOCTOR_HEADER), given.doctorId());
        return Result.ok(service.doctorReply(given.consultationId(), given.doctorId(), given.text()));
    }
}
