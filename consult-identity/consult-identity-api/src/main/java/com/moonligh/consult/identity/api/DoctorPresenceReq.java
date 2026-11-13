package com.moonligh.consult.identity.api;

/**
 * 医生上线或下线。上线进入当前在手单数对应队列的队尾，下线从三层队列拿掉。
 */
public record DoctorPresenceReq(String doctorId) {
}
