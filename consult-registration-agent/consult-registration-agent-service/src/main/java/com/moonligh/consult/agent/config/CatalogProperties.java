package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 目录开通门禁。演示环境允许当场补医生。
 */
@ConfigurationProperties(prefix = "consult.catalog")
public class CatalogProperties {

    /** true 时开通二级若缺人会向身份服务补演示医生。 */
    private boolean provisionDemo = true;

    public boolean isProvisionDemo() {
        return provisionDemo;
    }

    public void setProvisionDemo(boolean provisionDemo) {
        this.provisionDemo = provisionDemo;
    }
}
