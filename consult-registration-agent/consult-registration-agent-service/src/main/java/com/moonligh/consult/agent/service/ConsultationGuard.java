package com.moonligh.consult.agent.service;

import java.util.function.Supplier;

/**
 * 同一轮咨询同一时刻只跑一个重操作（发消息、改卡、绑人、下单、改派）。
 * 连点和客户端超时重试会让同一会话并发跑两遍模型和派单，再在行锁上排队；这里在入口直接挡掉第二个。
 * 正确性仍由会话行锁保证，守卫失效只会多花资源，不会写错数据。
 */
public interface ConsultationGuard {

    ConsultationGuard NONE = new ConsultationGuard() {
        @Override
        public <T> T exclusive(String consultationId, Supplier<T> action) {
            return action.get();
        }
    };

    /** 拿不到时抛 CONSULT_IN_PROGRESS；consultationId 为空（新开一轮）直接执行。 */
    <T> T exclusive(String consultationId, Supplier<T> action);
}
