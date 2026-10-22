package com.moonligh.consult.agent.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jayway.jsonpath.JsonPath;
import com.moonligh.consult.agent.service.ConsultationService;
import com.moonligh.consult.agent.service.ConsultationsFixture;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.GlobalExceptionHandler;
import com.moonligh.consult.framework.ResultCode;
import com.moonligh.consult.framework.TraceIdFilter;

/** HTTP 包装：统一 Result、追踪号、错误码。不启动完整 Spring。 */
class ConsultationControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ConsultationService service = ConsultationsFixture.shadow().service;
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsultationController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void ac1ReturnsWrappedJson() throws Exception {
        mockMvc.perform(post("/api/consultations/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"女，32岁，干咳半年，运动后加重\"}"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.HEADER))
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.message").value(ResultCode.SUCCESS.getMessage()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.consultationId").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.card.gender").value(2))
                .andExpect(jsonPath("$.data.card.age").value(32))
                .andExpect(jsonPath("$.data.card.duration").value("半年"))
                .andExpect(jsonPath("$.data.card.symptoms[0]").value("干咳"))
                .andExpect(jsonPath("$.data.card.department").value(1));
    }

    @Test
    void patchAndConfirmUseBodyId() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/consultations/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"女，32岁，干咳半年，运动后加重\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String consultationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.consultationId");

        mockMvc.perform(patch("/api/consultations/card")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":\"" + consultationId + "\",\"department\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.card.department").value(4));

        mockMvc.perform(post("/api/consultations/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":\"" + consultationId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.card.department").value(4));
    }

    @Test
    void unknownConsultationReturnsBizError() throws Exception {
        mockMvc.perform(post("/api/consultations/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":\"6f1c3a2e-4b7d-4e1a-9c2f-0a1b2c3d4e5f\",\"text\":\"女\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONSULTATION_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("找不到这轮咨询。"))
                .andExpect(jsonPath("$.data").value(nullValue()));
    }
}
