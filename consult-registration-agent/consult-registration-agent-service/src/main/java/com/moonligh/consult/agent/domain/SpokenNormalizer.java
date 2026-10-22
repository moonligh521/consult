package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.domain.department.DepartmentSeed;
import com.moonligh.consult.agent.domain.department.DepartmentSnapshot;
import com.moonligh.consult.agent.domain.department.MatchTypo;

/**
 * 匹配用口语归一：去空格后再把快照里的错别字换成目录词。
 * 只给科室路由和症状抽取用。急症闸门继续走 {@link CompactText}，避免把 DRAFT 影子纠成基线 120。
 */
public final class SpokenNormalizer {

    private SpokenNormalizer() {
    }

    /** 匹配用紧凑串。空输入得到空串。单测无快照时用种子。 */
    public static String forMatch(String original) {
        return forMatch(original, DepartmentSeed.load());
    }

    public static String forMatch(String original, DepartmentSnapshot snapshot) {
        String text = CompactText.of(original);
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
