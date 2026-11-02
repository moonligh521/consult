package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.req.PatientCreateReq;
import com.moonligh.consult.agent.api.res.ConsultationRes;
import com.moonligh.consult.agent.api.res.PatientRes;
import com.moonligh.consult.framework.BusinessException;
import com.moonligh.consult.framework.ErrorCode;

/** 一个账号多位就诊人，可选已有的，也可以新建后再绑到本轮咨询。 */
class PatientFlowTest {

    private ConsultationsFixture fixture;
    private PatientService patients;
    private ConsultationService service;

    @BeforeEach
    void setUp() {
        fixture = ConsultationsFixture.shadow();
        patients = new PatientService(fixture.patients);
        service = fixture.service;
    }

    @Test
    void oneAccountCanKeepManyPatients() {
        PatientRes self = patients.create("demo", new PatientCreateReq("张明", Gender.MALE, 32, "本人"));
        PatientRes child = patients.create("demo", new PatientCreateReq("张小童", Gender.MALE, 8, "子女"));
        patients.create("other", new PatientCreateReq("别人", Gender.FEMALE, 40, "本人"));

        List<PatientRes> demo = patients.list("demo");
        assertEquals(2, demo.size());
        assertEquals(self.id(), demo.get(0).id());
        assertTrue(demo.get(0).isDefault());
        assertEquals(child.id(), demo.get(1).id());
        assertEquals(1, patients.list("other").size());
    }

    @Test
    void bindPatientFillsGenderAgeAndName() {
        PatientRes self = patients.create("demo", new PatientCreateReq("张明", Gender.MALE, 32, "本人"));
        ConsultationRes first = service.acceptMessage(null, "干咳半年，运动后加重");
        assertEquals(ConsultationStatus.NEED_SLOT, first.status());

        ConsultationRes bound = service.bindPatient(first.consultationId(), self.id(), "demo");
        assertEquals(ConsultationStatus.CARD_READY, bound.status());
        assertEquals(Gender.MALE, bound.card().gender());
        assertEquals(32, bound.card().age());
        assertEquals("半年", bound.card().duration());
        assertEquals(self.id(), bound.card().patientId());
        assertEquals("张明", bound.card().patientName());
    }

    @Test
    void cannotBindSomeoneElsesPatient() {
        PatientRes other = patients.create("other", new PatientCreateReq("李芳", Gender.FEMALE, 28, "本人"));
        ConsultationRes first = service.acceptMessage(null, "头痛三天");
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.bindPatient(first.consultationId(), other.id(), "demo"));
        assertEquals(ErrorCode.PATIENT_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void createRejectsBlankName() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> patients.create("demo", new PatientCreateReq("  ", Gender.MALE, 20, "本人")));
        assertEquals(ErrorCode.INVALID_PATIENT, error.getErrorCode());
    }
}
