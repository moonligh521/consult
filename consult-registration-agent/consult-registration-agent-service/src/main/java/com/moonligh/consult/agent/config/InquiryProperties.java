package com.moonligh.consult.agent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 问诊通道开关。关闭后不能确认卡片、不能下单。
 */
@ConfigurationProperties(prefix = "inquiry")
public class InquiryProperties {

    /** true 时整条支付链路关闭。默认开放。 */
    private boolean closed = false;
    /** 本档排满后，队头等这么久再提一档。 */
    private Duration matchUpgradeAfter = Duration.ofMinutes(1);

    public boolean isClosed() {
        return closed;
    }

    public void setClosed(boolean closed) {
        this.closed = closed;
    }

    public Duration getMatchUpgradeAfter() {
        return matchUpgradeAfter == null ? Duration.ofMinutes(1) : matchUpgradeAfter;
    }

    public void setMatchUpgradeAfter(Duration matchUpgradeAfter) {
        this.matchUpgradeAfter = matchUpgradeAfter;
    }
}
