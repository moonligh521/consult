package com.moonligh.consult.framework.mq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;

import com.fasterxml.jackson.databind.ObjectMapper;

class ConsultMqSenderTest {

    @Test
    void destinationJoinsTopicAndTag() {
        assertEquals("consult-order-sync:ASSIGNED", ConsultMqTopics.orderAssigned());
        assertEquals("t", ConsultMqDestinations.of("t", ""));
        assertThrows(IllegalArgumentException.class, () -> ConsultMqDestinations.of(" ", "tag"));
    }

    @Test
    void syncSendWritesJsonAndKeysWithoutNeedingBroker() {
        RocketMQTemplate template = mock(RocketMQTemplate.class);
        when(template.syncSend(eq(ConsultMqTopics.orderAssigned()), org.mockito.ArgumentMatchers.<Message<String>>any()))
                .thenReturn(new SendResult());
        ConsultMqSender sender = new ConsultMqSender(template, new ObjectMapper());
        sender.syncSendJson(ConsultMqTopics.orderAssigned(), Map.of("orderId", "o1"), "o1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<String>> captor = ArgumentCaptor.forClass(Message.class);
        verify(template).syncSend(eq(ConsultMqTopics.orderAssigned()), captor.capture());
        assertEquals("{\"orderId\":\"o1\"}", captor.getValue().getPayload());
        assertEquals("o1", captor.getValue().getHeaders().get(RocketMQHeaders.KEYS));
    }
}
