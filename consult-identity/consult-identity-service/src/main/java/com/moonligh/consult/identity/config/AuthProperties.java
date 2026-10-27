package com.moonligh.consult.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 身份服务自己的登录配置。JWT 和内部令牌在 consult-framework-security。
 */
@ConfigurationProperties(prefix = "consult.auth")
public class AuthProperties {

    private int smsTtlSeconds = 300;
    private int smsIntervalSeconds = 60;
    /** 演示验证码。生产应留空，只发真实短信。 */
    private String smsDevCode = "000000";
    private String adminUsername = "admin";
    private String adminPassword = "admin1234";

    public int getSmsTtlSeconds() {
        return smsTtlSeconds;
    }

    public void setSmsTtlSeconds(int smsTtlSeconds) {
        this.smsTtlSeconds = smsTtlSeconds;
    }

    public int getSmsIntervalSeconds() {
        return smsIntervalSeconds;
    }

    public void setSmsIntervalSeconds(int smsIntervalSeconds) {
        this.smsIntervalSeconds = smsIntervalSeconds;
    }

    public String getSmsDevCode() {
        return smsDevCode;
    }

    public void setSmsDevCode(String smsDevCode) {
        this.smsDevCode = smsDevCode;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
