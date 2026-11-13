package com.moonligh.consult.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.res.AssignedDoctorRes;
import com.moonligh.consult.agent.api.res.DoctorLinePollRes;
import com.moonligh.consult.agent.domain.DoctorAssigner;
import com.moonligh.consult.agent.infrastructure.line.MemoryDoctorLineCursor;

class DoctorLinePollTest {

    @Test
    void unchangedSeqSkipsTheOrderRead() {
        ConsultationsFixture fixture = ConsultationsFixture.shadow();
        fixture.service.setDoctorAssigner(fixedDoctor());
        fixture.service.setLineCursor(new MemoryDoctorLineCursor());
        String id = fixture.service.acceptMessage(null, null, "女，32岁，干咳半年，运动后加重", "acc-1").consultationId();
        fixture.service.confirm(id);
        fixture.service.placeOrder(id, 1, 15, null);
        fixture.service.doctorReply(id, "doc-1", "已看到病历，先按慢病随访。");

        DoctorLinePollRes first = fixture.service.linesSince("acc-1", id, 0);
        assertTrue(first.changed());
        assertTrue(first.lines().stream().anyMatch(line -> line.text().contains("慢病随访")));
        int afterRead = fixture.repository.reads();

        DoctorLinePollRes again = fixture.service.linesSince("acc-1", id, first.seq());
        assertFalse(again.changed());
        assertTrue(again.lines().isEmpty());
        assertEquals(afterRead, fixture.repository.reads());
    }

    private static DoctorAssigner fixedDoctor() {
        return (consultationId, orderId, card, terms) -> new AssignedDoctorRes(
                "doc-1", "周医生", "主治医师", "市医院", 1, "呼吸内科", null, "干咳", "sch-1");
    }
}
