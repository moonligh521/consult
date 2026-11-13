package com.moonligh.consult.agent.infrastructure.notice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moonligh.consult.agent.api.event.OrderAssignedEvent;
import com.moonligh.consult.agent.domain.notice.AssignmentOutbox;
import com.moonligh.consult.framework.mq.ConsultMqSender;
import com.moonligh.consult.framework.mq.ConsultMqTopics;

/**
 * 先同步发派单消息。Broker 拒绝时把事件放进 Redis 列表，对账时再发。
 * 不把扣号放进事务消息。号已经在 MySQL 里占上。
 */
public final class MqAssignmentOutbox implements AssignmentOutbox {

    private static final Logger log = LoggerFactory.getLogger(MqAssignmentOutbox.class);
    private static final String PENDING = "consult:outbox:assigned";
    private static final int BATCH = 100;

    private final ConsultMqSender sender;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public MqAssignmentOutbox(ConsultMqSender sender, StringRedisTemplate redis, ObjectMapper mapper) {
        this.sender = sender;
        this.redis = redis;
        this.mapper = mapper;
    }

    @Override
    public void accept(OrderAssignedEvent event) {
        if (event == null || event.orderId() == null || event.orderId().isBlank()) {
            return;
        }
        try {
            send(event);
        } catch (RuntimeException error) {
            remember(event);
        }
    }

    @Override
    public int drain() {
        int sent = 0;
        for (int i = 0; i < BATCH; i++) {
            String raw;
            try {
                raw = redis.opsForList().leftPop(PENDING);
            } catch (RuntimeException error) {
                return sent;
            }
            if (raw == null) {
                return sent;
            }
            OrderAssignedEvent event;
            try {
                event = mapper.readValue(raw, OrderAssignedEvent.class);
                send(event);
                sent++;
            } catch (RuntimeException | JsonProcessingException error) {
                redis.opsForList().leftPush(PENDING, raw);
                return sent;
            }
        }
        return sent;
    }

    private void send(OrderAssignedEvent event) {
        sender.syncSendJson(ConsultMqTopics.orderAssigned(), event, event.orderId());
    }

    private void remember(OrderAssignedEvent event) {
        try {
            redis.opsForList().rightPush(PENDING, mapper.writeValueAsString(event));
        } catch (RuntimeException | JsonProcessingException error) {
            log.warn("订单同步医生端失败 orderId={}", event.orderId());
        }
    }
}
