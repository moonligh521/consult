package com.moonligh.consult.identity.domain.load;

/**
 * 一次领取时允许的医生。idleSinceMillis 为 0 表示还没有空闲时间，入队时用当前时间，排到队尾。
 */
public record DoctorSeat(String doctorId, int inFlight, long idleSinceMillis, int concurrentLimit) {

    public DoctorSeat(String doctorId, int inFlight, long idleSinceMillis) {
        this(doctorId, inFlight, idleSinceMillis, 3);
    }

    public DoctorSeat {
        if (concurrentLimit < 1) {
            concurrentLimit = 3;
        }
    }
}
