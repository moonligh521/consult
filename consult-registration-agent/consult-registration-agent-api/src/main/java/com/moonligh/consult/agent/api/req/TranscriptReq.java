package com.moonligh.consult.agent.api.req;

/**
 * 把当前诊室画面存回会话，供咨询记录回看。
 */
public record TranscriptReq(
        String consultationId,
        String title,
        String transcript) {
}
