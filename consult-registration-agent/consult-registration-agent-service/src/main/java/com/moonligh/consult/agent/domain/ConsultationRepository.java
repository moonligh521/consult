package com.moonligh.consult.agent.domain;

import java.time.Instant;
import java.util.List;

import com.moonligh.consult.agent.api.res.ConsultationRecordRes;
import com.moonligh.consult.agent.api.res.ConsultationRes;

/**
 * 咨询会话的存取。
 * 同一咨询编号的请求必须排队，避免两条消息互相覆盖安全状态。
 */
public interface ConsultationRepository {

    /**
     * 按编号取出会话并加锁，办完 action 再保存、解锁。
     * 编号为空则新建一轮。找不到已有编号则抛 1101。
     * action 里抛出的业务错误会在保存之后再抛出，避免原话回滚丢失。
     */
    ConsultationRes runLocked(String consultationId, SessionAction action);

    /** 不加锁读出会话。找不到返回 null。 */
    ConsultationSession get(String consultationId);

    /** 当前账号一年窗口内的记录，按最近更新倒序。 */
    List<ConsultationRecordRes> listMine(String accountId, Instant from, int offset, int limit);

    long countMine(String accountId, Instant from);
}
