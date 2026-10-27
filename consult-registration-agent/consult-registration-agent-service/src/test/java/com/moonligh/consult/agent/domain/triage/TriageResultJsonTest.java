package com.moonligh.consult.agent.domain.triage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.DepartmentRouter;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;
import com.moonligh.consult.framework.model.ModelStatus;

/** triage_json 存进去能原样读回，派单才能不重算。 */
class TriageResultJsonTest {

    @Test
    void roundTrips() throws Exception {
        MemoryDepartmentCatalog catalog = MemoryDepartmentCatalog.seed();
        TriageEngine engine = new TriageEngine(catalog, new DepartmentRouter(catalog),
                (d, a, g, t, c) -> new ComplaintReply(ModelStatus.SUCCESS,
                        List.of(new ComplaintReply.ModelTerm("高血压", "头晕")), null, null, 8, false, "v", "m"));
        TriageResult result = engine.run(new TriageInput("头晕乏力一周", 50, Gender.MALE)).result();
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(result);
        assertFalse(json.contains("头晕乏力一周"), "不存原话");
        TriageResult back = mapper.readValue(json, TriageResult.class);
        assertEquals(result, back);
        assertEquals(List.of("高血压"), back.canonicalTerms());
    }
}
