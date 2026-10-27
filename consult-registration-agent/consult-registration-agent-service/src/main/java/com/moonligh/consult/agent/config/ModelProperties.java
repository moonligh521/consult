package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.moonligh.consult.agent.infrastructure.model.ModelPurpose;

/**
 * 大模型网关配置。对应 application.yml 里的 model。不设开关：缺密钥时网关回报不可用，各节点按兜底走。
 */
@ConfigurationProperties(prefix = "model")
public class ModelProperties {

    /** 密钥。不要打进日志。 */
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String modelName = "deepseek-v4-flash";
    /** 模型结果缓存小时数。 */
    private int cacheTtlHours = 168;
    private final Lane safety = new Lane(2500, 32, 50, 1);
    private final Lane complaint = new Lane(2000, 32, 50, 0);

    public Lane lane(ModelPurpose purpose) {
        return purpose == ModelPurpose.SAFETY ? safety : complaint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getCacheTtlHours() {
        return cacheTtlHours;
    }

    public void setCacheTtlHours(int cacheTtlHours) {
        this.cacheTtlHours = cacheTtlHours;
    }

    public Lane getSafety() {
        return safety;
    }

    public Lane getComplaint() {
        return complaint;
    }

    /** 一类调用的超时、并发与重试。超时按压测 p99×1.2 定。 */
    public static class Lane {
        private int timeoutMs;
        private int maxConcurrency;
        private int acquireWaitMs;
        private int invalidOutputRetries;

        public Lane() {
        }

        Lane(int timeoutMs, int maxConcurrency, int acquireWaitMs, int invalidOutputRetries) {
            this.timeoutMs = timeoutMs;
            this.maxConcurrency = maxConcurrency;
            this.acquireWaitMs = acquireWaitMs;
            this.invalidOutputRetries = invalidOutputRetries;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getAcquireWaitMs() {
            return acquireWaitMs;
        }

        public void setAcquireWaitMs(int acquireWaitMs) {
            this.acquireWaitMs = acquireWaitMs;
        }

        public int getInvalidOutputRetries() {
            return invalidOutputRetries;
        }

        public void setInvalidOutputRetries(int invalidOutputRetries) {
            this.invalidOutputRetries = invalidOutputRetries;
        }
    }
}
