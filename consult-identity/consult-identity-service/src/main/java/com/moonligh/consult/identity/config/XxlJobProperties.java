package com.moonligh.consult.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * XXL-Job 执行器。调度中心地址和令牌只从本机 yml 或环境变量读。
 */
@ConfigurationProperties(prefix = "consult.xxl")
public class XxlJobProperties {

    /** false 时本进程不向调度中心报名，方便单测。 */
    private boolean enabled;
    /** 调度中心根地址，不要带 /xxl-job-admin。 */
    private String adminAddresses = "http://127.0.0.1:18080";
    /** 和控制台「执行器 AppName」一致。 */
    private String appname = "consult-identity";
    /** 留空则自动探测网卡。和调度中心不在同一网络时不要指望回连成功。 */
    private String ip = "";
    private int port = 19999;
    /** 3.4.2 默认 default_token，和调度中心保持一致。 */
    private String accessToken = "";
    private String logPath = "./logs/xxl-job";
    private int logRetentionDays = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAdminAddresses() {
        return adminAddresses;
    }

    public void setAdminAddresses(String adminAddresses) {
        this.adminAddresses = adminAddresses;
    }

    public String getAppname() {
        return appname;
    }

    public void setAppname(String appname) {
        this.appname = appname;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getLogPath() {
        return logPath;
    }

    public void setLogPath(String logPath) {
        this.logPath = logPath;
    }

    public int getLogRetentionDays() {
        return logRetentionDays;
    }

    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }
}
