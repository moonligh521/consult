package com.moonligh.consult.agent.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 同一账号同一句原话在窗口内只开一轮。Redis 单飞失效时，这一笔仍能挡住第二次插入。
 */
public interface OpeningClaim {

    Duration WINDOW = Duration.ofSeconds(8);

    /**
     * 占住这句话。返回实际归属的咨询编号。
     * 与传入编号相同表示这次占住了，调用方继续创建；不同表示窗口内已有一轮。
     */
    String claim(String accountId, String text, String consultationId, Instant now);

    /** 窗口里那一轮已经结束，改记到这一轮。 */
    void replace(String accountId, String text, String consultationId, Instant now);
}
