package com.moonligh.benefit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "benefit")
public class BenefitProperties {

    /** 调用方和本服务约定的 X-Benefit-Token。空着则拒绝全部接口。 */
    private String token = "";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
