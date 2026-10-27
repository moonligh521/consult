package com.moonligh.consult.agent.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUIDv7：前 48 位是毫秒时间，后面是随机数。主键按时间递增插入，B+ 树不再随机分裂；
 * 日后按月分区也能从编号推出时间。随机部分 74 位，匿名会话靠编号访问时仍猜不到。
 */
public final class TimeOrderedIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TimeOrderedIds() {
    }

    public static String next() {
        long millis = System.currentTimeMillis();
        long msb = (millis << 16) | 0x7000L | (RANDOM.nextInt() & 0x0FFFL);
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }
}
