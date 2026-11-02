package com.moonligh.consult.identity.domain;

/**
 * 医生空出坑位。挂号服务用来弹出该科该档队头。
 */
public interface SlotFreedSink {

    SlotFreedSink NONE = (specialty, grade) -> {
    };

    void freed(String specialty, int grade);
}
