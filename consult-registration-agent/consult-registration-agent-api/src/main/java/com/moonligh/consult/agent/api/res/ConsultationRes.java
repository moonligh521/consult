package com.moonligh.consult.agent.api.res;

import com.moonligh.consult.agent.api.enums.ConsultationStatus;
import com.moonligh.consult.agent.api.enums.Handoff;
import com.moonligh.consult.agent.api.enums.MissingSlot;

/**
 * 一轮咨询的当前结果。三个接口都返回这个对象。
 */
public record ConsultationRes(
        /** 本轮咨询编号，后续请求都带上它。 */
        String consultationId,
        /** 当前状态，JSON 里是数字 code。 */
        ConsultationStatus status,
        /** 给患者看的下一句提示。 */
        String prompt,
        /** 还缺哪个槽位；不需要追问时为 null。 */
        MissingSlot missingSlot,
        /** 信息齐了才有卡片；急症或还在追问时为 null。 */
        ConsultationCardRes card,
        /** 需尽快评估时的提示；否则为 null。 */
        String safetyNotice,
        /** 有卡片时的交接性质；安全停止态为 null。 */
        Handoff handoff) {
}
