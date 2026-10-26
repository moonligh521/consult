package com.moonligh.consult.identity.api;

/**
 * 用户改问诊时长，从当前时刻重新计算到期。
 */
public record ExtendDoctorReq(String orderId, Integer consultMinutes) {
}
