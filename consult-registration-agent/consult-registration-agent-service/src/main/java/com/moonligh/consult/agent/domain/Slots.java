package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.enums.MissingSlot;

/**
 * 槽位追问顺序：性别 → 年龄 → 病程。
 */
public final class Slots {

    private Slots() {
    }

    /** 下一个还没填的槽位。都齐了返回 null。 */
    public static MissingSlot nextMissing(ConsultationSession session) {
        // 顺序固定：先性别，再年龄，再病程。
        if (session.getGender() == null) {
            return MissingSlot.GENDER;
        }
        if (session.getAge() == null) {
            return MissingSlot.AGE;
        }
        if (session.getDuration() == null) {
            return MissingSlot.DURATION;
        }
        return null;
    }

    /** 追问这句话怎么说。缺性别或年龄时先选账号下的就诊人。 */
    public static String promptFor(MissingSlot slot) {
        if (slot == MissingSlot.GENDER || slot == MissingSlot.AGE) {
            return "请问您想为以下哪位患者咨询?";
        }
        return "症状大概多久了？点选下方时长，或自己填写。";
    }
}
