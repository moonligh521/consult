package com.moonligh.consult.framework.mq;

/**
 * RocketMQ destination：{@code topic} 或 {@code topic:tag}。
 */
public final class ConsultMqDestinations {

    private ConsultMqDestinations() {
    }

    public static String of(String topic, String tag) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic 不能为空");
        }
        if (tag == null || tag.isBlank()) {
            return topic.trim();
        }
        return topic.trim() + ":" + tag.trim();
    }
}
