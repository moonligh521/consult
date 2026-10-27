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
    /**
     * 并发是单实例上限，供应商侧总并发 = 实例数 × maxConcurrency，按账号限额核算。
     * 模型已在会话锁外调用，排队等一会儿不再占数据库连接，比 50ms 拿不到就判繁忙、整单安全降级划算。
     */
    private final Lane safety = new Lane(2500, 32, 1500, 1);
    private final Lane complaint = new Lane(2000, 32, 800, 0);

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
        /** 全部实例合计的并发上限，按供应商账号限额配；0 表示不设全局配额，只靠单实例 maxConcurrency。 */
        private int globalMaxConcurrency;

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

        public int getGlobalMaxConcurrency() {
            return globalMaxConcurrency;
        }

        public void setGlobalMaxConcurrency(int globalMaxConcurrency) {
            this.globalMaxConcurrency = globalMaxConcurrency;
        }
    }
}
