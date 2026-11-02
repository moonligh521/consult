package com.moonligh.consult.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class DoctorMatcherTest {

    private static final DoctorMatcher.Candidate ZHOU = candidate("doc-zhou-ning", "周宁", 1, "呼吸内科", "干咳、咳嗽、哮喘、肺部感染");
    private static final DoctorMatcher.Candidate ZHAO = candidate("doc-zhao-heng", "赵衡", 1, "普内科", "高血压、常见内科随访");
    private static final DoctorMatcher.Candidate SUN = candidate("doc-sun-yue", "孙悦", 1, "普内科", "糖尿病、高血脂");
    private static final DoctorMatcher.Candidate QI = candidate("doc-wang-qi", "王启明", 4, "全科", "常见病初诊分流");

    @Test
    void hypertensionGoesToDoctorWhoseSkillsMatch() {
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(ZHOU, ZHAO, SUN, QI), "普内科", 1, "咨询高血压随访");
        assertEquals("赵衡", pick.doctor().name());
        assertEquals("高血压", pick.evidence());
    }

    @Test
    void diabetesGoesToOtherDoctorInSameSpecialty() {
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(ZHOU, ZHAO, SUN, QI), "普内科", 1, "糖尿病血糖控制不佳");
        assertEquals("孙悦", pick.doctor().name());
        assertEquals("糖尿病", pick.evidence());
    }

    @Test
    void coughFindsRespiratoryBySkillsEvenIfCardSaysGeneralInternal() {
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(ZHOU, ZHAO, SUN, QI), "普内科", 1, "干咳一周");
        assertEquals("周宁", pick.doctor().name());
        assertEquals("干咳", pick.evidence());
    }

    @Test
    void coughStaysWithRespiratoryWhenSpecialtyMatches() {
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(ZHOU, ZHAO, SUN, QI), "呼吸内科", 1, "干咳半年，运动后加重");
        assertEquals("周宁", pick.doctor().name());
        assertEquals("干咳", pick.evidence());
    }

    @Test
    void sameSpecialtyWithoutSkillHitStillStaysInPool() {
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(ZHAO, SUN), "普内科", 1, "乏力一周");
        assertEquals("普内科", pick.doctor().specialty());
        assertEquals(0, pick.score());
    }

    @Test
    void lighterDoctorWinsWhenSkillsTie() {
        DoctorMatcher.Candidate busy = candidate("busy", "赵衡", 1, "普内科", "高血压", 2, 10);
        DoctorMatcher.Candidate free = candidate("free", "孙悦", 1, "普内科", "高血压", 0, 10);
        DoctorMatcher.Pick pick = DoctorMatcher.pick(List.of(busy, free), "普内科", 1, "高血压");
        assertEquals("孙悦", pick.doctor().name());
        assertEquals("高血压", pick.evidence());
    }

    @Test
    void missingSpecialtyFallsBackToGeneral() {
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(QI), "不存在的科", 99, "高血压");
        assertEquals("王启明", pick.doctor().name());
    }

    @Test
    void emptyPoolIsNull() {
        assertNull(DoctorMatcher.pick(List.of(), "普内科", 1, "高血压"));
    }

    @Test
    void canonTermMatchesSkillWithoutDiseaseWordInText() {
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(ZHOU, ZHAO, SUN, QI), "普内科", 1, "量血压160/100，头胀两天", List.of("高血压"));
        assertEquals("赵衡", pick.doctor().name());
        assertEquals(DoctorMatcher.MatchSource.SKILL_CANON, pick.source());
    }

    @Test
    void exactCanonBeatsLongerTextHit() {
        DoctorMatcher.Candidate text = candidate("text", "甲", 1, "普内科", "常见内科随访");
        DoctorMatcher.Candidate canon = candidate("canon", "乙", 1, "普内科", "痛风");
        DoctorMatcher.Pick pick = DoctorMatcher.pick(
                List.of(text, canon), "普内科", 1, "常见内科随访，尿酸高", List.of("痛风"));
        assertEquals("乙", pick.doctor().name());
        assertEquals(DoctorMatcher.MatchSource.SKILL_CANON, pick.source());
    }

    @Test
    void sourceReflectsFallbackLevel() {
        assertEquals(DoctorMatcher.MatchSource.SKILL_TEXT,
                DoctorMatcher.pick(List.of(ZHAO, SUN), "普内科", 1, "高血压").source());
        assertEquals(DoctorMatcher.MatchSource.LOAD_L2,
                DoctorMatcher.pick(List.of(ZHAO, SUN), "普内科", 1, "乏力一周").source());
        assertEquals(DoctorMatcher.MatchSource.LOAD_L1,
                DoctorMatcher.pick(List.of(ZHAO, SUN), "内分泌科", 1, "乏力一周").source());
        assertEquals(DoctorMatcher.MatchSource.GENERAL,
                DoctorMatcher.pick(List.of(QI), "不存在的科", 99, "乏力").source());
    }

    private static DoctorMatcher.Candidate candidate(
            String id, String name, int dept, String specialty, String skills) {
        return candidate(id, name, dept, specialty, skills, 0, 20);
    }

    private static DoctorMatcher.Candidate candidate(
            String id, String name, int dept, String specialty, String skills, int inFlight, int remainNum) {
        return new DoctorMatcher.Candidate(
                id, name, "主治医师", "互联网医院", dept, specialty, skills, inFlight, remainNum, "sch-" + id);
    }
}
