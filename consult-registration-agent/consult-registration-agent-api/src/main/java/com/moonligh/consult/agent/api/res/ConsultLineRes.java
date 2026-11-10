package com.moonligh.consult.agent.api.res;

import java.time.Instant;

/** 诊室里的一条原话。source：1 患者，2 改卡，3 医生。 */
public record ConsultLineRes(int seq, int source, String text, Instant at) {
}
