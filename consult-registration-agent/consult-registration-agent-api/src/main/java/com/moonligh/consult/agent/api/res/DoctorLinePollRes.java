package com.moonligh.consult.agent.api.res;

import java.util.List;

/**
 * 患者拉医生回复。changed 为假时 lines 为空，调用方沿用已经显示的消息。
 */
public record DoctorLinePollRes(int seq, boolean changed, List<ConsultLineRes> lines) {
}
