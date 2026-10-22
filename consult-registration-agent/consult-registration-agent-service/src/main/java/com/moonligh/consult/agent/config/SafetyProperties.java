package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全分流配置。对应 application.yml 里的 safety。
 */
@ConfigurationProperties(prefix = "safety")
public class SafetyProperties {

    private final Catalog catalog = new Catalog();
    private final Model model = new Model();

    public Catalog getCatalog() {
        return catalog;
    }

    public Model getModel() {
        return model;
    }

    /** 急症目录从哪读、DRAFT 规则怎么处理。 */
    public static class Catalog {
        /** classpath 路径。 */
        private String location = "classpath:safety/urgent-catalog.json";
        /** 生产必须是 SHADOW。 */
        private String draftMode = "SHADOW";

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getDraftMode() {
            return draftMode;
        }

        public void setDraftMode(String draftMode) {
            this.draftMode = draftMode;
        }

        /** 转成 /safety/xxx.json，给 Class.getResourceAsStream 用。 */
        public String classpathLocation() {
            String value = location;
            if (value.startsWith("classpath:")) {
                value = value.substring("classpath:".length());
            }
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            return value;
        }
    }

    /** 模型补漏。默认关闭。 */
    public static class Model {
        /** 没开就不调模型。 */
        private boolean enabled = false;
        /** 单次调用超时毫秒。 */
        private int timeoutMs = 3000;
        /** 输出不合格时再试几次。 */
        private int invalidOutputRetries = 1;
        /** 同时最多几路调用。 */
        private int maxConcurrency = 20;
        /** 密钥。不要打进日志。 */
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com";
        private String modelName = "deepseek-chat";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getInvalidOutputRetries() {
            return invalidOutputRetries;
        }

        public void setInvalidOutputRetries(int invalidOutputRetries) {
            this.invalidOutputRetries = invalidOutputRetries;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
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
    }
}
