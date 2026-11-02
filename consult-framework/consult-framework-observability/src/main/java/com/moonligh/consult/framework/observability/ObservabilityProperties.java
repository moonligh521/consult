package com.moonligh.consult.framework.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 观测开关。默认打开；Collector 不可用时仍 no-op，不挡挂号。
 */
@ConfigurationProperties(prefix = "consult.observability")
public class ObservabilityProperties {

    /** 为 false 时全程 no-op。 */
    private boolean enabled = true;
    /** 为 false 时只记元数据，不写主诉和模型原文。 */
    private boolean capturePayloads = true;
    private final Langfuse langfuse = new Langfuse();
    private final Judge judge = new Judge();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCapturePayloads() {
        return capturePayloads;
    }

    public void setCapturePayloads(boolean capturePayloads) {
        this.capturePayloads = capturePayloads;
    }

    public Langfuse getLangfuse() {
        return langfuse;
    }

    public Judge getJudge() {
        return judge;
    }

    public static class Langfuse {
        private String publicUrl = "";
        private String projectId = "";
        private int retentionDays = 30;
        /** Scores API。不要打进日志，只写 local yml。 */
        private String publicKey = "";
        private String secretKey = "";

        public String getPublicUrl() {
            return publicUrl;
        }

        public void setPublicUrl(String publicUrl) {
            this.publicUrl = publicUrl;
        }

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }

    /**
     * 旁路话术评分。不进患者请求，不走 ModelGateway 舱壁。
     * bootstrap/recheck=窗口全量；steady=0.2%。
     */
    public static class Judge {
        private boolean enabled = false;
        private String phase = "bootstrap";
        private int sinceMinutes = 180;
        private long intervalMs = 900_000;
        private int maxObservations = 500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPhase() {
            return phase;
        }

        public void setPhase(String phase) {
            this.phase = phase;
        }

        public int getSinceMinutes() {
            return sinceMinutes;
        }

        public void setSinceMinutes(int sinceMinutes) {
            this.sinceMinutes = sinceMinutes;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public int getMaxObservations() {
            return maxObservations;
        }

        public void setMaxObservations(int maxObservations) {
            this.maxObservations = maxObservations;
        }
    }
}
