package com.moonligh.consult.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimitBucketTest {

    @Test
    void burstThenRefillByElapsedTime() {
        long start = 0;
        RateLimitGatewayFilter.Bucket bucket = new RateLimitGatewayFilter.Bucket(3, start);
        assertTrue(bucket.tryTake(2, 3, start));
        assertTrue(bucket.tryTake(2, 3, start));
        assertTrue(bucket.tryTake(2, 3, start));
        assertFalse(bucket.tryTake(2, 3, start));
        // 每秒补 2 个，半秒后补满 1 个
        assertTrue(bucket.tryTake(2, 3, start + 500_000_000L));
        assertFalse(bucket.tryTake(2, 3, start + 500_000_000L));
    }

    @Test
    void neverExceedsBurst() {
        RateLimitGatewayFilter.Bucket bucket = new RateLimitGatewayFilter.Bucket(2, 0);
        long later = 60_000_000_000L;
        assertTrue(bucket.tryTake(5, 2, later));
        assertTrue(bucket.tryTake(5, 2, later));
        assertFalse(bucket.tryTake(5, 2, later));
    }
}
