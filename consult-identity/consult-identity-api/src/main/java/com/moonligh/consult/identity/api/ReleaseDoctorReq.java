package com.moonligh.consult.identity.api;

/**
 * 问诊结束或超时释放占坑。reason：ENDED / TIMEOUT / CANCEL。
 */
public record ReleaseDoctorReq(String orderId, String reason) {
}
