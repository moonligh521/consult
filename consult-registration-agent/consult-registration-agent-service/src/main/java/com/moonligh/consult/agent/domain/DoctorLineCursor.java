package com.moonligh.consult.agent.domain;

import java.util.OptionalInt;

/**
 * 一条咨询上最新医生消息的序号。轮询先看它，没变就不读订单。
 */
public interface DoctorLineCursor {

    /** 还没有写过，或这一次读失败，返回 empty，调用方回源数据库。 */
    OptionalInt read(String consultationId);

    /** 只向前推进。更小的序号不能把已经写下的盖掉。 */
    void publish(String consultationId, int seq);
}
