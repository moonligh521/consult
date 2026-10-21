package com.moonligh.consult.agent.domain;

import com.moonligh.consult.agent.api.enums.MissingSlot;

/**
 * 槽位追问顺序：性别 → 年龄 → 病程。
 */
public final class Slots {

    private Slots() {
    }

    public static MissingSlot nextMissing(ConsultationSession session) {
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
