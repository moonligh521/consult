package com.moonligh.consult.agent.domain.notice;

import com.moonligh.consult.agent.api.event.OrderAssignedEvent;

/**
 * 占坑成功后通知医生端。发送失败先记下，对账任务再补发。同一订单号由消费端去重。
 */
public interface AssignmentOutbox {

    void accept(OrderAssignedEvent event);

    /** 把上次没发出去的通知再发一遍。返回本拍发出的条数。 */
    int drain();
}
