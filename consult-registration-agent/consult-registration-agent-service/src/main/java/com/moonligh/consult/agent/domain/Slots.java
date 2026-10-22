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

    /** 追问这句话怎么说。 */
    public static String promptFor(MissingSlot slot) {
        if (slot == MissingSlot.GENDER) {
            return "请补充性别。";
        }
        if (slot == MissingSlot.AGE) {
            return "请补充年龄。";
        }
        return "请补充病程。";
    }
}
