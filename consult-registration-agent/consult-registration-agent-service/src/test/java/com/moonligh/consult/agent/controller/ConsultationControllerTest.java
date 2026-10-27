package com.moonligh.consult.agent.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.jayway.jsonpath.JsonPath;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.req.PatientCreateReq;
import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.agent.service.ConsultationsFixture;
import com.moonligh.consult.agent.service.PatientService;
import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.framework.GlobalExceptionHandler;
import com.moonligh.consult.framework.ResultCode;
import com.moonligh.consult.framework.TraceIdFilter;

/** HTTP 包装：统一 Result、追踪号、错误码。不启动完整 Spring。 */
class ConsultationControllerTest {

    private MockMvc mockMvc;
    private PatientService patients;

    @BeforeEach
    void setUp() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        patients = new PatientService(fixture.patients);
        mockMvc = MockMvcBuilders.standaloneSetup(new ConsultationController(fixture.service))
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
                .andExpect(jsonPath("$.data.card.department").value(1))
                .andExpect(jsonPath("$.data.card.specialty").value("呼吸内科"));
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
                .andExpect(jsonPath("$.data.card.department").value(4))
                .andExpect(jsonPath("$.data.card.specialty").value("全科"));

        mockMvc.perform(post("/api/consultations/confirmation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":\"" + consultationId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.card.department").value(4))
                .andExpect(jsonPath("$.data.card.specialty").value("全科"))
                .andExpect(jsonPath("$.data.packages.length()").value(4))
                .andExpect(jsonPath("$.data.order").value(nullValue()));

        mockMvc.perform(get("/api/consultations/packages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data[2].code").value(3))
                .andExpect(jsonPath("$.data[2].name").value("三甲医生"));

        mockMvc.perform(post("/api/consultations/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":\"" + consultationId + "\",\"packageCode\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.status").value(5))
                .andExpect(jsonPath("$.data.order.packageCode").value(3))
                .andExpect(jsonPath("$.data.order.packageName").value("三甲医生"))
                .andExpect(jsonPath("$.data.order.payStatus").value(2));

        mockMvc.perform(post("/api/consultations/reassign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":\"" + consultationId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.DOCTOR_UNAVAILABLE.getCode()));
    }

    @Test
    void listMineRequiresAccount() throws Exception {
        mockMvc.perform(get("/api/consultations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_REQUIRED.getCode()));
    }

    @Test
    void waitAssignmentRequiresAccount() throws Exception {
        mockMvc.perform(get("/api/consultations/c1/assignment").param("waitMs", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ErrorCode.LOGIN_REQUIRED.getCode()));
    }

    @Test
    void createListAndBindPatient() throws Exception {
        PatientRes first = patients.create("demo", new PatientCreateReq("张明", Gender.MALE, 32, "本人"));
        patients.create("demo", new PatientCreateReq("张小童", Gender.MALE, 8, "子女"));
        assertEquals(2, patients.list("demo").size());
        assertEquals("张明", patients.list("demo").get(0).name());
        assertTrue(patients.list("other").isEmpty());

        MvcResult created = mockMvc.perform(post("/api/consultations/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"干咳半年，运动后加重\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andReturn();
        String consultationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.consultationId");
        String patientId = first.id();

        mockMvc.perform(post("/api/consultations/patient")
                        .header("X-Account-Id", "demo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consultationId\":\"" + consultationId + "\",\"patientId\":\"" + patientId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(4))
                .andExpect(jsonPath("$.data.card.patientName").value("张明"))
                .andExpect(jsonPath("$.data.card.gender").value(1))
                .andExpect(jsonPath("$.data.card.age").value(32));
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
