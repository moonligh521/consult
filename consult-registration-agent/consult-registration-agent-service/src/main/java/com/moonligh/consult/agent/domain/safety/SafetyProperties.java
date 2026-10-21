package com.moonligh.consult.agent.domain.safety;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全分流配置。
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

    public static class Catalog {
        private String location = "classpath:safety/urgent-catalog.json";
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

    public static class Model {
        private boolean enabled = false;
        private int timeoutMs = 3000;
        private int invalidOutputRetries = 1;
        private int maxConcurrency = 20;
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
