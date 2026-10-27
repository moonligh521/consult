package com.moonligh.consult.framework.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 封装层开关。连接仍用 {@code rocketmq.name-server}。没配 name-server 时不会创建 Sender。
 */
@ConfigurationProperties(prefix = "consult.mq")
public class ConsultMqProperties {

    /** false 时不注册 {@link ConsultMqSender}。 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
