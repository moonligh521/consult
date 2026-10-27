package com.moonligh.consult.framework.mq;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RocketMQ 发送。载荷转 JSON；不打印正文（可能含病情）。消费端用 {@code @RocketMQMessageListener}。
 */
public class ConsultMqSender {

    private static final Logger log = LoggerFactory.getLogger(ConsultMqSender.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper mapper;

    public ConsultMqSender(RocketMQTemplate rocketMQTemplate, ObjectMapper mapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.mapper = mapper == null ? new ObjectMapper() : mapper;
    }

    public SendResult syncSendJson(String destination, Object payload) {
        return syncSendJson(destination, payload, null);
    }

    public SendResult syncSendJson(String topic, String tag, Object payload) {
        return syncSendJson(ConsultMqDestinations.of(topic, tag), payload, null);
    }

    /**
     * @param keys 幂等键，例如订单号；消费端按这个去重
     */
    public SendResult syncSendJson(String destination, Object payload, String keys) {
        requireDestination(destination);
        SendResult result = rocketMQTemplate.syncSend(destination, message(payload, keys));
        log.debug("MQ syncSend destination={} msgId={}", destination, result == null ? null : result.getMsgId());
        return result;
    }

    /**
     * 事务消息。本地事务监听器由业务注册 {@code @RocketMQTransactionListener}。
     */
    public SendResult sendTransactionJson(String destination, Object payload, String keys, Object arg) {
        requireDestination(destination);
        SendResult result = rocketMQTemplate.sendMessageInTransaction(destination, message(payload, keys), arg);
        log.debug("MQ transactionSend destination={} msgId={}", destination, result == null ? null : result.getMsgId());
        return result;
    }

    public RocketMQTemplate template() {
        return rocketMQTemplate;
    }

    private Message<String> message(Object payload, String keys) {
        if (payload == null) {
            throw new IllegalArgumentException("payload 不能为空");
        }
        String body = payload instanceof String text ? text : writeJson(payload);
        MessageBuilder<String> builder = MessageBuilder.withPayload(body);
        if (keys != null && !keys.isBlank()) {
            builder.setHeader(RocketMQHeaders.KEYS, keys);
        }
        return builder.build();
    }

    private static void requireDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination 不能为空");
        }
    }

    private String writeJson(Object payload) {
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("MQ 载荷无法序列化", error);
        }
    }
}
