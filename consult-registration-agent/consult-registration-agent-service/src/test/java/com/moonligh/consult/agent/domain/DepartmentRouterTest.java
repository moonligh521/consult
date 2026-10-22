package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;

class DepartmentRouterTest {

    private static final Set<String> OWNED = Set.of(
            "内科", "外科", "男科", "全科", "骨科", "妇产科", "口腔科", "儿科",
            "耳鼻咽喉头颈外科", "中医科", "皮肤性病科", "感染病科", "眼科", "精神心理科",
            "整形美容科", "营养科", "肿瘤科", "生殖医学中心", "麻醉医学科", "睡眠医学科",
            "中西医结合科", "康复医学科");

    private final DepartmentRouter router = new DepartmentRouter(MemoryDepartmentCatalog.seed());

    @Test
    void ownedDepartmentsAreTwentyTwo() {
        assertEquals(OWNED, Arrays.stream(Department.values()).map(Department::getLabel).collect(Collectors.toSet()));
        assertEquals(22, Department.values().length);
    }

    @Test
    void coughGoesRespiratory() {
        DepartmentRouter.Advice advice = router.advise("干咳半年");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("呼吸内科", advice.specialty());
        assertEquals("干咳", advice.evidence());
    }

    @Test
    void headacheGoesNeurology() {
        DepartmentRouter.Advice advice = router.advise("头痛两天");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("神经内科", advice.specialty());
        assertEquals("头痛", advice.evidence());
    }

    @Test
    void stomachPainGoesGastro() {
        DepartmentRouter.Advice advice = router.advise("胃痛三天");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("消化内科", advice.specialty());
        assertEquals("胃痛", advice.evidence());
    }

    @Test
    void fractureGoesTraumaOrtho() {
        DepartmentRouter.Advice advice = router.advise("手腕骨折两天");
        assertEquals(Department.ORTHOPEDICS, advice.department());
        assertEquals("创伤骨科", advice.specialty());
        assertEquals("骨折", advice.evidence());
    }

    @Test
    void unmatchedFallsToGeneral() {
        DepartmentRouter.Advice advice = router.advise("体检报告看不懂");
        assertEquals(Department.GENERAL, advice.department());
        assertEquals("全科", advice.specialty());
        assertNull(advice.evidence());
        assertEquals("全科", router.advise("").specialty());
        assertEquals("全科", router.advise("   ").specialty());
    }

    @Test
    void skinAndEyeHangToOwnSpecialties() {
        assertEquals("皮肤科", router.advise("手上长湿疹").specialty());
        assertEquals(Department.DERMATOLOGY, router.advise("手上长湿疹").department());
        assertEquals("眼科", router.advise("眼睛红，有分泌物").specialty());
        assertEquals(Department.OPHTHALMOLOGY, router.advise("眼睛红，有分泌物").department());
        assertEquals("皮肤科", router.advise("起了一片红疹").specialty());
        assertEquals("红疹", router.advise("起了一片红疹").evidence());
        assertEquals("皮肤科", router.advise("挂皮肤科").specialty());
        assertEquals("眼科", router.advise("挂眼科").specialty());
    }

    @Test
    void spacesDoNotLetLaterKeStealDiarrhea() {
        DepartmentRouter.Advice advice = router.advise("腹 泻 ， 咳 嗽");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("消化内科", advice.specialty());
        assertEquals("腹泻", advice.evidence());
    }

    @Test
    void spacedCoughUsesLongerKeyword() {
        DepartmentRouter.Advice advice = router.advise("咳 嗽");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("呼吸内科", advice.specialty());
        assertEquals("咳嗽", advice.evidence());
    }

    @Test
    void oralSynonymsRouteToOwnedSpecialties() {
        assertEquals("消化内科", router.advise("拉肚子三天").specialty());
        assertEquals("消化内科", router.advise("想吐，吃不下").specialty());
        assertEquals("神经内科", router.advise("脑袋疼两天").specialty());
        assertEquals("神经内科", router.advise("偏头疼又犯了").specialty());
        assertEquals("耳鼻咽喉科", router.advise("打喷嚏流鼻涕").specialty());
        assertEquals("耳鼻咽喉科", router.advise("睡觉打呼噜").specialty());
        assertEquals(Department.ENT, router.advise("打喷嚏流鼻涕").department());
        assertEquals("口腔科", router.advise("牙疼三天").specialty());
        assertEquals("胃肠外科", router.advise("阑尾炎复查").specialty());
        assertEquals("男科", router.advise("前列腺复查").specialty());
        assertEquals("妇科", router.advise("月经不调").specialty());
        assertEquals("中医科", router.advise("咳嗽，想看中医").specialty());
        assertEquals(Department.TCM, router.advise("咳嗽，想看中医").department());
    }

    @Test
    void spokenInternalLeaksRouteToInternal() {
        for (String text : new String[] {
                "喉咙痒，老想清嗓子", "嗓子呼噜呼噜的", "脑袋昏昏沉沉", "整宿睡不着",
                "脑子迷糊", "站着要晕", "太阳穴跳着疼", "记性越来越差", "脑子里嗡嗡响",
                "肚子里咕噜叫", "吃不下饭，看见饭就腻", "大便不成形", "放屁特别臭",
                "嘴里发苦", "吃完就跑厕所", "食欲差，瘦了", "饭后打饱嗝", "上腹部堵得慌",
                "肚脐上面疼"
        }) {
            assertEquals(Department.INTERNAL, router.advise(text).department(), text);
        }
    }

    @Test
    void heelPainGoesFootAnkleAndParonychiaGoesSurgery() {
        assertEquals("足踝外科", router.advise("足跟疼").specialty());
        assertEquals(Department.ORTHOPEDICS, router.advise("足跟疼").department());
        assertEquals("普外科", router.advise("甲沟炎").specialty());
        assertEquals(Department.SURGERY, router.advise("甲沟炎").department());
    }

    @Test
    void spokenTyposRouteToInternal() {
        assertEquals("神经内科", router.advise("头通两天").specialty());
        assertEquals("头痛", router.advise("头通两天").evidence());
        assertEquals("呼吸内科", router.advise("刻嗽三天").specialty());
        assertEquals("咳嗽", router.advise("刻嗽三天").evidence());
        assertEquals(Department.GENERAL, router.advise("没有呼息困难").department());
        assertEquals(Department.GENERAL, router.advise("好像呼息困难").department());
    }

    @Test
    void uncertaintyFallsToGeneralWithoutBreakingFirstWin() {
        assertEquals(Department.GENERAL, router.advise("肚子疼，说不清哪个位置").department());
        assertEquals(Department.GENERAL, router.advise("恶心还咳嗽，不知道挂哪个科").department());
        assertEquals(Department.GENERAL, router.advise("咳嗽，腰也疼，不知道挂哪科").department());
        assertEquals(Department.GENERAL, router.advise("说不清是胃疼还是肚子疼").department());
        assertEquals(Department.GENERAL, router.advise("孩子一直哭，说不清哪儿疼").department());
        assertEquals("呼吸内科", router.advise("咳嗽还头痛").specialty());
        assertEquals("咳嗽", router.advise("咳嗽还头痛").evidence());
        assertEquals("神经内科", router.advise("头痛加咳嗽").specialty());
    }

    @Test
    void childMarkerAndAgeGoPediatrics() {
        assertEquals(Department.PEDIATRICS, router.advise("孩子咳嗽三天").department());
        assertEquals("小儿呼吸科", router.advise("孩子咳嗽三天").specialty());
        assertEquals("小儿呼吸科", router.advise("咳嗽三天", 8, null).specialty());
        assertEquals("呼吸内科", router.advise("咳嗽三天", 32, null).specialty());
    }

    @Test
    void urinaryRoutesByGender() {
        assertEquals(Department.OBGYN, router.advise("尿频尿急", null, Gender.FEMALE).department());
        assertEquals(Department.ANDROLOGY, router.advise("尿频尿急", null, Gender.MALE).department());
    }

    @Test
    void disabledDepartmentCannotBeMatched() {
        DepartmentRouter limited = new DepartmentRouter(
                MemoryDepartmentCatalog.seed().withoutEnabled(Department.ORTHOPEDICS.getCode()));
        assertEquals(Department.GENERAL, limited.advise("手腕骨折两天").department());
        assertEquals("全科", limited.advise("手腕骨折两天").specialty());
        assertEquals("呼吸内科", limited.advise("干咳半年").specialty());
    }

    @Test
    void neverLeavesOwnedSet() {
        for (String text : new String[] {
                "干咳半年", "头痛", "皮疹", "眼睛干", "看中医", "孩子发烧", "骨折", "牙痛",
                "鼻塞流鼻涕", "月经不调", "前列腺", "阑尾炎", "挂皮肤科", "挂眼科", "挂内分泌科"
        }) {
            assertTrue(OWNED.contains(router.advise(text).department().getLabel()), text);
        }
    }

    @Test
    void specialtyDescriptionsHangToSecondLevel() {
        assertEquals("消化内科", router.advise("胃溃疡复查").specialty());
        assertEquals("内分泌科", router.advise("挂内分泌科").specialty());
        assertEquals("耳鼻咽喉科", router.advise("鼻炎三天").specialty());
        assertEquals("肛肠外科", router.advise("痔疮出血").specialty());
        assertEquals("皮肤科", router.advise("手上白癜风").specialty());
        assertEquals("皮肤科", router.advise("挂皮肤科").specialty());
        assertEquals("骨科", router.advise("腰肌劳损").specialty());
        assertEquals("中医科", router.advise("肝肾两虚想调理").specialty());
    }

    @Test
    void categoryNamesHangToDefaultSecondLevel() {
        assertEquals("普内科", router.advise("挂内科").specialty());
        assertEquals(Department.INTERNAL, router.advise("挂内科").department());
        assertEquals("普外科", router.advise("挂外科").specialty());
        assertEquals(Department.SURGERY, router.advise("挂外科").department());
        assertEquals("皮肤科", router.advise("挂皮肤性病科").specialty());
        assertEquals(Department.DERMATOLOGY, router.advise("挂皮肤性病科").department());
        assertEquals("耳鼻咽喉科", router.advise("挂耳鼻咽喉头颈外科").specialty());
        assertEquals(Department.ENT, router.advise("挂耳鼻咽喉头颈外科").department());
        assertEquals("康复科", router.advise("挂康复医学科").specialty());
    }

    @Test
    void adviceSpecialtyIsAlwaysSecondLevel() {
        var snapshot = MemoryDepartmentCatalog.seed().snapshot();
        Set<String> secondLevel = new HashSet<>();
        Set<String> categoryOnly = new HashSet<>();
        for (var dept : snapshot.enabled()) {
            for (var spec : dept.specialties()) {
                secondLevel.add(spec.name());
            }
            boolean sameName = dept.specialties().stream().anyMatch(spec -> spec.name().equals(dept.name()));
            if (!sameName) {
                categoryOnly.add(dept.name());
            }
        }
        for (String text : new String[] {
                "干咳半年", "挂内科", "挂外科", "挂皮肤性病科", "挂耳鼻咽喉头颈外科", "体检报告看不懂"
        }) {
            String specialty = router.advise(text).specialty();
            assertTrue(secondLevel.contains(specialty), text + " -> " + specialty);
            assertTrue(!categoryOnly.contains(specialty), text + " leaked category " + specialty);
        }
    }
}
