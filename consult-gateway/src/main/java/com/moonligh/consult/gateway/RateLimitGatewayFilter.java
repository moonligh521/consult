package com.moonligh.consult.gateway;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ipresolver.RemoteAddressResolver;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import com.moonligh.consult.framework.ErrorCode;
import com.moonligh.consult.security.AuthTokens;

import io.micrometer.core.instrument.MeterRegistry;
import reactor.core.publisher.Mono;

/**
 * 分档令牌桶限流，排在验令牌之后，账号头已由网关改写，客户端伪造不了。
 * 计数用 Spring Cloud Gateway 自带的 RedisRateLimiter（官方 Lua 脚本），所有网关实例共享；
 * Redis 出错时 5 秒内改用本实例内存桶，限流不失效、也不把 Redis 超时叠加到每个请求上。
 */
@Component
public class RateLimitGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGatewayFilter.class);
    /** 桶数超过这个值就清掉一分钟没动的，防止 IP 扫描把内存撑大。 */
    private static final int SWEEP_THRESHOLD = 100_000;
    private static final long IDLE_NANOS = 60_000_000_000L;
    private static final long SKIP_REDIS_NANOS = 5_000_000_000L;
    private static final String DEFAULT_TIER = "default";

    private final boolean enabled;
    private final List<Tier> tiers;
    private final Tier fallbackTier;
    private final RedisRateLimiter redis;
    private final RemoteAddressResolver addresses;
    private final MeterRegistry meters;
    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong(System.nanoTime());
    private volatile long skipRedisUntil;

    public RateLimitGatewayFilter(
            GatewayProperties properties, ObjectProvider<RedisRateLimiter> redis, MeterRegistry meters) {
        GatewayProperties.RateLimit config = properties.getRateLimit();
        this.enabled = config.isEnabled();
        this.meters = meters;
        this.tiers = new ArrayList<>();
        for (GatewayProperties.Rule rule : config.getRules()) {
            tiers.add(Tier.of(rule));
        }
        this.fallbackTier = new Tier(DEFAULT_TIER, List.of(), Set.of(), GatewayProperties.KeyBy.ACCOUNT,
                config.getPerSecond(), config.getBurst());
        this.redis = redis.getIfAvailable();
        if (this.redis != null) {
            for (Tier tier : allTiers()) {
                this.redis.getConfig().put(tier.name(), new RedisRateLimiter.Config()
                        .setReplenishRate(tier.perSecond())
                        .setBurstCapacity(tier.burst())
                        .setRequestedTokens(1));
            }
        } else {
            log.warn("没有 RedisRateLimiter，限流只在本网关实例内生效");
        }
        this.addresses = properties.getTrustedProxyHops() > 0
                ? XForwardedRemoteAddressResolver.maxTrustedIndex(properties.getTrustedProxyHops())
                : new RemoteAddressResolver() { };
    }

    @Override
    public int getOrder() {
        return -99;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled || "OPTIONS".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }
        Tier tier = match(exchange);
        String key = tier.name() + ":" + keyOf(exchange, tier.keyBy());
        if (redis == null || System.nanoTime() < skipRedisUntil) {
            return decide(exchange, chain, tier, takeLocal(tier, key));
        }
        return redis.isAllowed(tier.name(), key)
                .onErrorResume(error -> Mono.just(new RateLimiter.Response(true, Map.of(
                        RedisRateLimiter.REMAINING_HEADER, "-1"))))
                .flatMap(response -> {
                    if (redisFailed(response)) {
                        skipRedisUntil = System.nanoTime() + SKIP_REDIS_NANOS;
                        log.warn("限流 Redis 不可用，5 秒内改用本实例内存桶");
                        return decide(exchange, chain, tier, takeLocal(tier, key));
                    }
                    return decide(exchange, chain, tier, response.isAllowed());
                });
    }

    private Mono<Void> decide(ServerWebExchange exchange, GatewayFilterChain chain, Tier tier, boolean allowed) {
        if (allowed) {
            return chain.filter(exchange);
        }
        meters.counter("gateway_rate_limited_total", "tier", tier.name()).increment();
        return GatewayResponses.reject(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.TOO_MANY_REQUESTS);
    }

    /** RedisRateLimiter 访问 Redis 出错时放行并把剩余令牌写成 -1，以此区分"真放行"和"故障放行"。 */
    private static boolean redisFailed(RateLimiter.Response response) {
        return "-1".equals(response.getHeaders().get(RedisRateLimiter.REMAINING_HEADER));
    }

    Tier match(ServerWebExchange exchange) {
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();
        String method = exchange.getRequest().getMethod().name();
        for (Tier tier : tiers) {
            if (tier.matches(path, method)) {
                return tier;
            }
        }
        return fallbackTier;
    }

    private String keyOf(ServerWebExchange exchange, GatewayProperties.KeyBy keyBy) {
        if (keyBy == GatewayProperties.KeyBy.ACCOUNT) {
            String account = exchange.getRequest().getHeaders().getFirst(AuthTokens.ACCOUNT_HEADER);
            if (account != null && !account.isBlank()) {
                return "a:" + account;
            }
        }
        // X-Forwarded-For 解析出的是未解析地址，getHostString 两种情况都直接给 IP 字面量，不做反查
        InetSocketAddress remote = addresses.resolve(exchange);
        return "ip:" + (remote == null ? "unknown" : remote.getHostString());
    }

    private boolean takeLocal(Tier tier, String key) {
        long now = System.nanoTime();
        sweepIfLarge(now);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(tier.burst(), now));
        return bucket.tryTake(tier.perSecond(), tier.burst(), now);
    }

    private void sweepIfLarge(long now) {
        long last = lastSweep.get();
        if (buckets.size() < SWEEP_THRESHOLD || now - last < IDLE_NANOS || !lastSweep.compareAndSet(last, now)) {
            return;
        }
        buckets.entrySet().removeIf(entry -> entry.getValue().idleSince(now) > IDLE_NANOS);
    }

    private List<Tier> allTiers() {
        List<Tier> all = new ArrayList<>(tiers);
        all.add(fallbackTier);
        return all;
    }

    record Tier(String name, List<PathPattern> paths, Set<String> methods, GatewayProperties.KeyBy keyBy,
            int perSecond, int burst) {

        static Tier of(GatewayProperties.Rule rule) {
            if (rule.getName() == null || rule.getName().isBlank() || DEFAULT_TIER.equals(rule.getName())) {
                throw new IllegalArgumentException("限流规则必须有名字且不能叫 default");
            }
            if (rule.getPerSecond() < 1 || rule.getBurst() < rule.getPerSecond()) {
                throw new IllegalArgumentException("限流规则 " + rule.getName() + " 要求 perSecond>=1 且 burst>=perSecond");
            }
            List<PathPattern> patterns = rule.getPaths().stream()
                    .map(PathPatternParser.defaultInstance::parse)
                    .toList();
            Set<String> methods = rule.getMethods().stream()
                    .map(m -> m.toUpperCase(Locale.ROOT))
                    .collect(Collectors.toUnmodifiableSet());
            return new Tier(rule.getName(), patterns, methods, rule.getKeyBy(), rule.getPerSecond(), rule.getBurst());
        }

        boolean matches(PathContainer path, String method) {
            if (!methods.isEmpty() && !methods.contains(method)) {
                return false;
            }
            for (PathPattern pattern : paths) {
                if (pattern.matches(path)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 本实例内存令牌桶，Redis 不可用时兜底。按流逝时间补令牌，拿到一个才放行。 */
    static final class Bucket {
        private double tokens;
        private long updated;

        Bucket(int burst, long now) {
            this.tokens = burst;
            this.updated = now;
        }

        synchronized boolean tryTake(double perSecond, int burst, long now) {
            double refill = (now - updated) / 1_000_000_000.0 * perSecond;
            tokens = Math.min(burst, tokens + refill);
            updated = now;
            if (tokens < 1) {
                return false;
            }
            tokens -= 1;
            return true;
        }

        synchronized long idleSince(long now) {
            return now - updated;
        }
    }
}
