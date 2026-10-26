package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 问诊通道开关。关闭后不能确认卡片、不能下单。
 */
@ConfigurationProperties(prefix = "inquiry")
public class InquiryProperties {

    /** true 时整条支付链路关闭。默认开放。 */
    private boolean closed = false;

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }
}
