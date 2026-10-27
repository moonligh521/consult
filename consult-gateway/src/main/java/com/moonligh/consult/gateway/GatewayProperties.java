package com.moonligh.consult.gateway;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关路由到的内部地址、JWT 密钥、限流与过载保护。
 */
@ConfigurationProperties(prefix = "consult.gateway")
public class GatewayProperties {

    private String identityUri = "http://127.0.0.1:18082";
    private String agentUri = "http://127.0.0.1:18081";
    private String jwtSecret = "consult-dev-jwt-secret-key-32bytes!";
    private int jwtTtlHours = 168;
    /** 单个网关实例同时在转发的请求上限，超过直接 503，挡住突发把后端连接池和模型配额一起打满。0 不限。 */
    private int maxInFlight = 4000;
    /**
     * 网关前面可信代理（SLB、Ingress）的层数。0 直接用 TCP 来源地址；大于 0 时从 X-Forwarded-For 右数这么多层取客户端 IP，
     * 只信自己的代理加的那几段，客户端伪造的左侧部分不认。
     */
    private int trustedProxyHops = 0;
    private final RateLimit rateLimit = new RateLimit();

    public int getTrustedProxyHops() {
        return trustedProxyHops;
    }

    public void setTrustedProxyHops(int trustedProxyHops) {
        this.trustedProxyHops = trustedProxyHops;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

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

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public int getJwtTtlHours() {
        return jwtTtlHours;
    }

    public void setJwtTtlHours(int jwtTtlHours) {
        this.jwtTtlHours = jwtTtlHours;
    }

    public int getMaxInFlight() {
        return maxInFlight;
    }

    public void setMaxInFlight(int maxInFlight) {
        this.maxInFlight = maxInFlight;
    }

    /**
     * 令牌桶限流。计数放 Redis，所有网关实例共用一份；Redis 不可用时退回本实例内存桶。
     * 按顺序匹配 rules，第一条命中的生效；都没命中用默认档 perSecond / burst。
     */
    public static class RateLimit {
        private boolean enabled = true;
        /** 默认档每秒补充的令牌数。 */
        private int perSecond = 5;
        /** 默认档桶容量，允许的瞬时突发。 */
        private int burst = 20;
        private List<Rule> rules = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPerSecond() {
            return perSecond;
        }

        public void setPerSecond(int perSecond) {
            this.perSecond = perSecond;
        }

        public int getBurst() {
            return burst;
        }

        public void setBurst(int burst) {
            this.burst = burst;
        }

        public List<Rule> getRules() {
            return rules;
        }

        public void setRules(List<Rule> rules) {
            this.rules = rules;
        }
    }

    /** 一档限流规则。 */
    public static class Rule {
        private String name;
        /** Ant 风格路径，如 /api/auth/sms、/api/consultations/**。 */
        private List<String> paths = new ArrayList<>();
        /** 为空表示所有方法。 */
        private List<String> methods = new ArrayList<>();
        private KeyBy keyBy = KeyBy.ACCOUNT;
        private int perSecond;
        private int burst;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public KeyBy getKeyBy() {
            return keyBy;
        }

        public void setKeyBy(KeyBy keyBy) {
            this.keyBy = keyBy;
        }

        public int getPerSecond() {
            return perSecond;
        }

        public void setPerSecond(int perSecond) {
            this.perSecond = perSecond;
        }

        public int getBurst() {
            return burst;
        }

        public void setBurst(int burst) {
            this.burst = burst;
        }
    }

    /** ACCOUNT：有账号按账号、没有按 IP；IP：只按来源 IP，用于登录、发验证码这类未登录接口防刷。 */
    public enum KeyBy {
        ACCOUNT,
        IP
    }
}
