package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.api.res.ConsultationCardRes;

class CardFactsTest {

    @Test
    void extractsAllergyAndMedication() {
        ConsultationCardRes card = CardFacts.compose(
                Gender.FEMALE,
                32,
                "半年",
                "干咳半年，青霉素过敏，在吃降压药",
                List.of("干咳"),
                new DepartmentRouter.Advice(Department.INTERNAL, "咳"));
        assertEquals("青霉素过敏", card.allergy());
        assertEquals("降压药", card.medications());
        assertEquals("咳", card.departmentEvidence());
        assertTrue(card.associatedSymptoms().isEmpty());
    }

    @Test
    void laterSymptomsBecomeAssociated() {
        ConsultationCardRes card = CardFacts.compose(
                Gender.MALE,
                40,
                "三天",
                "咳嗽三天，伴有头痛",
                List.of("咳嗽", "头痛"),
                new DepartmentRouter.Advice(Department.INTERNAL, "咳"));
        assertEquals(List.of("头痛"), card.associatedSymptoms());
    }

    @Test
    void oldSixFieldCardJsonStillLoads() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ConsultationCardRes card = mapper.readValue(
                "{\"gender\":2,\"age\":32,\"duration\":\"半年\",\"description\":\"干咳半年\",\"symptoms\":[\"干咳\"],\"department\":1}",
                ConsultationCardRes.class);
        assertEquals(Gender.FEMALE, card.gender());
        assertEquals(32, card.age());
        assertEquals(Department.INTERNAL.getCode(), card.department());
        assertTrue(card.associatedSymptoms().isEmpty());
        assertNull(card.allergy());
        assertNull(card.medications());
        assertNull(card.departmentEvidence());
    }
}
