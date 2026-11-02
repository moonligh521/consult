package com.moonligh.consult.framework.mq;

/**
 * 跨服务约定的 Topic。问诊订单同步走事务消息；放号仍用 Redis Stream。
 */
public final class ConsultMqTopics {

    /** 挂号派单成功后同步医生端。 */
    public static final String ORDER_SYNC = "consult-order-sync";
    /** 已派上医生。 */
    public static final String TAG_ASSIGNED = "ASSIGNED";

    private ConsultMqTopics() {
    }

    public static String orderAssigned() {
        return ConsultMqDestinations.of(ORDER_SYNC, TAG_ASSIGNED);
    }
}
