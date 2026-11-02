package com.moonligh.consult.framework.redis;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redisson 连接。单机地址默认跟 spring.data.redis；哨兵/集群用 config-file。
 */
@ConfigurationProperties(prefix = "consult.redisson")
public class RedissonProperties {

    /** false 时不创建客户端，业务按各自兜底走。 */
    private boolean enabled = true;
    /** 哨兵/集群时指向 Redisson YAML；为空用 spring.data.redis 单机地址。 */
    private String configFile = "";
    private Duration timeout = Duration.ofSeconds(1);
    private int poolSize = 64;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigFile() {
        return configFile;
    }

    public void setConfigFile(String configFile) {
        this.configFile = configFile;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }
}
