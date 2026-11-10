package com.moonligh.consult.identity.domain.load;

/**
 * 从某一层队列领出的医生。lane 是领取前的在手单数，score 是领取前的排队时间戳。
 * MySQL 占坑失败时按这两个值放回原位。
 */
public record DoctorClaim(String doctorId, int lane, long score) {
}
