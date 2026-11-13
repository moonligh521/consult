package com.moonligh.consult.identity.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moonligh.consult.framework.Result;
import com.moonligh.consult.identity.api.DoctorInboxRes;
import com.moonligh.consult.identity.domain.AssignmentNoticeStore;
import com.moonligh.consult.identity.domain.AssignmentNoticeStore.PendingNotice;
import com.moonligh.consult.security.AuthTokens;
import com.moonligh.consult.security.DoctorDeskGuard;

import jakarta.servlet.http.HttpServletRequest;

/** 医生待接箱。派单成功后从这里取咨询编号，再去挂号服务回复。只认令牌里绑定的医生。 */
@RestController
@RequestMapping("/api/doctor/inbox")
public class DoctorDeskController {

    private final AssignmentNoticeStore notices;

    public DoctorDeskController(AssignmentNoticeStore notices) {
        this.notices = notices;
    }

    @GetMapping
    public Result<List<DoctorInboxRes>> inbox(HttpServletRequest request, @RequestParam String doctorId) {
        DoctorDeskGuard.same(request.getHeader(AuthTokens.DOCTOR_HEADER), doctorId);
        List<DoctorInboxRes> rows = new ArrayList<>();
        for (PendingNotice notice : notices.inbox(doctorId.trim(), 50)) {
            rows.add(new DoctorInboxRes(
                    notice.orderId(),
                    notice.consultationId(),
                    notice.specialty(),
                    notice.scheduleId()));
        }
        return Result.ok(rows);
    }
}
