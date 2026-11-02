package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.domain.department.DepartmentSeed;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MatchTypo;

/**
 * 匹配用口语归一：去空格、繁体改简体，再把快照里的错别字换成目录词。
 * 只给科室路由和症状抽取用。急症闸门继续走 {@link CompactText}，避免把 DRAFT 影子纠成基线 120。
 * 拼音、英文不在这里展开，规则认不出就交给主诉模型。
 */
public final class SpokenNormalizer {

    /**
     * 挂号常见繁体字。逐字替换，不收录拼音。
     */
    private static final String TRAD =
            "頭瀉膚過內壓結經調陰慮鬱視鳴腫癢濕瘡貧頸關節質鬆燒嘔惡暈眩蕁腎傷燙齒齦潰瘍啞聾癇";
    private static final String SIMP =
            "头泻肤过内压结经调阴虑郁视鸣肿痒湿疮贫颈关节质松烧呕恶晕眩荨肾伤烫齿龈溃疡哑聋痫";

    private SpokenNormalizer() {
    }

    static String toSimplified(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int at = TRAD.indexOf(ch);
            out.append(at >= 0 ? SIMP.charAt(at) : ch);
        }
        return out.toString();
    }

    /** 匹配用紧凑串。空输入得到空串。单测无快照时用种子。 */
    public static String forMatch(String original) {
        return forMatch(original, DepartmentSeed.load());
    }

    public static String forMatch(String original, DepartmentSnapshot snapshot) {
        String text = toSimplified(CompactText.of(original));
        if (snapshot == null) {
            return text;
        }
        for (MatchTypo typo : snapshot.typos()) {
            text = text.replace(typo.from(), typo.to());
        }
        return text;
    }

    /** 原话里带「说不清挂哪科」这类标记。 */
    public static boolean uncertainDepartment(String original) {
        return uncertainDepartment(original, DepartmentSeed.load());
    }

    public static boolean uncertainDepartment(String original, DepartmentSnapshot snapshot) {
        String text = CompactText.of(original);
        if (snapshot == null) {
            return false;
        }
        for (String marker : snapshot.uncertainMarkers()) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
