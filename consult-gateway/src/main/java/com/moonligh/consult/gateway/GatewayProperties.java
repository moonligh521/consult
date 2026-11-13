package com.moonligh.consult.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 挂号产品网关路由目标。限流 / 在途 / 匿名路径见 moonligh.gateway（platform-gateway）。
 */
@ConfigurationProperties(prefix = "consult.gateway")
public class GatewayProperties {

    private String identityUri = "http://127.0.0.1:18082";
    private String agentUri = "http://127.0.0.1:18081";
    private String paymentUri = "http://127.0.0.1:18083";
    private String benefitUri = "http://127.0.0.1:18084";
    private String nutritionistUri = "http://127.0.0.1:18091";

    public String getIdentityUri() {
        return identityUri;
    }

    public void setIdentityUri(String identityUri) {
        this.identityUri = identityUri;
    }

    public String getAgentUri() {
        return agentUri;
    }

    public void setAgentUri(String agentUri) {
        this.agentUri = agentUri;
    }

    public String getPaymentUri() {
        return paymentUri;
    }

    public void setPaymentUri(String paymentUri) {
        this.paymentUri = paymentUri;
    }

    public String getBenefitUri() {
        return benefitUri;
    }

    public void setBenefitUri(String benefitUri) {
        this.benefitUri = benefitUri;
    }

    public String getNutritionistUri() {
        return nutritionistUri;
    }

    public void setNutritionistUri(String nutritionistUri) {
        this.nutritionistUri = nutritionistUri;
    }
}

