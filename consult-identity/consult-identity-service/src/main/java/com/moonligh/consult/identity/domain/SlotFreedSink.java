package com.moonligh.consult.identity.domain;

import com.moonligh.consult.identity.api.SlotFreed;

/**
 * 医生空出坑位。挂号服务用来弹出该科该档队头。
 */
public interface SlotFreedSink {

    SlotFreedSink NONE = notice -> {
    };

    void freed(SlotFreed notice);
}
