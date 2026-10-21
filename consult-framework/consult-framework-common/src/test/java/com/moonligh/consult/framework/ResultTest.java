package com.moonligh.consult.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 确认成功和业务失败时，统一返回的 code、message、data 约定。 */
class ResultTest {

    @Test
    void okWrapsData() {
        Result<String> result = Result.ok("ok");
        assertTrue(result.isSuccess());
        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals("ok", result.getData());
    }

    @Test
    void failUsesErrorCode() {
        Result<Void> result = Result.fail(ErrorCode.CONSULTATION_NOT_FOUND);
        assertFalse(result.isSuccess());
        assertEquals(1101, result.getCode());
        assertEquals("找不到这轮咨询。", result.getMessage());
        assertNull(result.getData());
    }
}
