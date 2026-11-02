package com.moonligh.consult.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 安全分流配置。对应 application.yml 里的 safety。
 */
@ConfigurationProperties(prefix = "safety")
public class SafetyProperties {

    private final Catalog catalog = new Catalog();

    public Catalog getCatalog() {
        return catalog;
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
}
