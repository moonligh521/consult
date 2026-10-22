package com.moonligh.consult.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.moonligh.consult.agent.api.enums.Department;
import com.moonligh.consult.agent.api.enums.Gender;
import com.moonligh.consult.agent.domain.department.MemoryDepartmentCatalog;

class DepartmentRouterTest {

    private static final Set<String> OWNED = Set.of(
            "内科", "外科", "男科", "全科", "骨科", "妇产科", "口腔科", "儿科", "耳鼻喉科", "中医科");

    private final DepartmentRouter router = new DepartmentRouter(MemoryDepartmentCatalog.seed());

    @Test
    void ownedDepartmentsAreExactlyTen() {
        assertEquals(OWNED, Arrays.stream(Department.values()).map(Department::getLabel).collect(Collectors.toSet()));
        assertEquals(10, Department.values().length);
    }

    @Test
    void coughGoesInternalWithKeywordEvidence() {
        DepartmentRouter.Advice advice = router.advise("干咳半年");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("干咳", advice.evidence());
    }

    @Test
    void headacheGoesInternal() {
        DepartmentRouter.Advice advice = router.advise("头痛两天");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("头痛", advice.evidence());
    }

    @Test
    void stomachPainGoesInternal() {
        DepartmentRouter.Advice advice = router.advise("胃痛三天");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("胃痛", advice.evidence());
    }

    @Test
    void fractureGoesOrthopedics() {
        DepartmentRouter.Advice advice = router.advise("手腕骨折两天");
        assertEquals(Department.ORTHOPEDICS, advice.department());
        assertEquals("骨折", advice.evidence());
    }

    @Test
    void unmatchedFallsToGeneral() {
        DepartmentRouter.Advice advice = router.advise("体检报告看不懂");
        assertEquals(Department.GENERAL, advice.department());
        assertNull(advice.evidence());
        assertEquals(Department.GENERAL, router.advise("挂皮肤科").department());
        assertEquals(Department.GENERAL, router.advise("挂眼科").department());
        assertEquals(Department.GENERAL, router.advise("").department());
        assertEquals(Department.GENERAL, router.advise("   ").department());
    }

    @Test
    void skinAndEyeStayGeneral() {
        assertEquals(Department.GENERAL, router.advise("手上长湿疹").department());
        assertEquals(Department.GENERAL, router.advise("眼睛红，有分泌物").department());
        assertEquals("红疹", router.advise("起了一片红疹").evidence());
    }

    @Test
    void spacesDoNotLetLaterKeStealDiarrhea() {
        DepartmentRouter.Advice advice = router.advise("腹 泻 ， 咳 嗽");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("腹泻", advice.evidence());
    }

    @Test
    void spacedCoughUsesLongerKeyword() {
        DepartmentRouter.Advice advice = router.advise("咳 嗽");
        assertEquals(Department.INTERNAL, advice.department());
        assertEquals("咳嗽", advice.evidence());
    }

    @Test
    void oralSynonymsRouteToOwnedSpecialties() {
        assertEquals(Department.INTERNAL, router.advise("拉肚子三天").department());
        assertEquals(Department.INTERNAL, router.advise("想吐，吃不下").department());
        assertEquals(Department.INTERNAL, router.advise("脑袋疼两天").department());
        assertEquals(Department.INTERNAL, router.advise("偏头疼又犯了").department());
        assertEquals(Department.ENT, router.advise("打喷嚏流鼻涕").department());
        assertEquals(Department.ENT, router.advise("睡觉打呼噜").department());
        assertEquals(Department.STOMATOLOGY, router.advise("牙疼三天").department());
        assertEquals(Department.SURGERY, router.advise("阑尾炎复查").department());
        assertEquals(Department.ANDROLOGY, router.advise("前列腺复查").department());
        assertEquals(Department.OBGYN, router.advise("月经不调").department());
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
    void heelPainGoesOrthopedicsAndParonychiaGoesSurgery() {
        assertEquals(Department.ORTHOPEDICS, router.advise("足跟疼").department());
        assertEquals(Department.SURGERY, router.advise("甲沟炎").department());
    }

    @Test
    void spokenTyposRouteToInternal() {
        assertEquals(Department.INTERNAL, router.advise("头通两天").department());
        assertEquals("头痛", router.advise("头通两天").evidence());
        assertEquals(Department.INTERNAL, router.advise("刻嗽三天").department());
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
        assertEquals(Department.INTERNAL, router.advise("咳嗽还头痛").department());
        assertEquals("咳嗽", router.advise("咳嗽还头痛").evidence());
        assertEquals(Department.INTERNAL, router.advise("头痛加咳嗽").department());
    }

    @Test
    void childMarkerAndAgeGoPediatrics() {
        assertEquals(Department.PEDIATRICS, router.advise("孩子咳嗽三天").department());
        assertEquals(Department.PEDIATRICS, router.advise("咳嗽三天", 8, null).department());
        assertEquals(Department.INTERNAL, router.advise("咳嗽三天", 32, null).department());
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
        assertEquals(Department.INTERNAL, limited.advise("干咳半年").department());
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
}
