package com.moonligh.consult.identity.domain.load;

import java.util.List;

/**
 * 医生同时在手单数队列。每个二级科、每个咨询档位各三层：0 单、1 单、2 单。满 3 单不在任何一层。
 * 先领 0 单层，再 1 单，再 2 单。同一层里时间戳更小的人先接。账本仍是 MySQL 条件更新。
 */
public interface DoctorLoadPool {

    /**
     * 在允许名单里领一人。同一订单号再调用返回同一个人，不重复加在手单数。
     * 没有可领的人返回 null。
     */
    DoctorClaim claim(String orderId, String specialty, int grade, List<DoctorSeat> allowed, long nowMillis);

    /** 数据库占坑失败。按领取前的层和时间戳放回去，并清掉这次订单的领取记录。 */
    void restore(String orderId, DoctorClaim claim, String specialty, int grade);

    /** 问诊结束，在手单数减一。人还在线就按释放时间进入对应层队尾。 */
    void release(String doctorId, String specialty, int grade, long nowMillis);

    /** 上线。按当前在手单数进入对应层队尾，不恢复下线前的旧时间戳。同时接诊上限按 3。 */
    void online(String doctorId, String specialty, int grade, int inFlight, long nowMillis);

    /** 上线，并带上这位医生自己的同时接诊上限。 */
    default void online(String doctorId, String specialty, int grade, int inFlight, int concurrentLimit, long nowMillis) {
        online(doctorId, specialty, grade, inFlight, nowMillis);
    }

    /** 下线。从三层队列移除，已在手的单数保留，释放时不再入队。 */
    void offline(String doctorId, String specialty, int grade);
}
