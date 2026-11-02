package com.moonligh.consult.framework.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveAttributeFilterTest {

    @Test
    void blocksIdentityAndSecrets() {
        assertTrue(SensitiveAttributeFilter.blockedKey("patient_name"));
        assertTrue(SensitiveAttributeFilter.blockedKey("Authorization"));
        assertTrue(SensitiveAttributeFilter.blockedKey("api-key"));
        assertTrue(SensitiveAttributeFilter.blockedKey("mobile"));
        assertTrue(SensitiveAttributeFilter.blockedKey("id_card"));
        assertTrue(SensitiveAttributeFilter.blockedValue("sk-abcdefghijklmnop"));
        assertTrue(SensitiveAttributeFilter.blockedValue("13812345678"));
        assertFalse(SensitiveAttributeFilter.blockedKey("specialty"));
        assertFalse(SensitiveAttributeFilter.blockedKey("langfuse.user.id"));
        assertFalse(SensitiveAttributeFilter.blockedKey("user_id"));
        assertTrue(SensitiveAttributeFilter.allow("specialty", "呼吸内科"));
        assertTrue(SensitiveAttributeFilter.allow("langfuse.user.id", "acct-87c3"));
        assertFalse(SensitiveAttributeFilter.allow("langfuse.user.id", "13800138000"));
    }
}
