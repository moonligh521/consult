package com.moonligh.consult.framework.redis;

/**
 * 跨服务约定的 Redis 键。业务协议名放这里，避免身份服务和挂号各写一份。
 */
public final class ConsultRedis {

    /** 医生放号。身份服务 XADD，挂号消费组可靠出队。 */
    public static final String SLOT_FREED_STREAM = "consult:stream:slot-freed";
    /** 挂号侧消费组。失败不 ACK，对账任务兜底。 */
    public static final String SLOT_FREED_GROUP = "consult-match";
    /** Stream 近似上限，挡住无限堆积。 */
    public static final long SLOT_FREED_MAXLEN = 20_000;

    private ConsultRedis() {
    }
}
