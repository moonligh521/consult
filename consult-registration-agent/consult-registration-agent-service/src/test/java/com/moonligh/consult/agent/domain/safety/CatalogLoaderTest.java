package com.moonligh.consult.agent.domain.safety;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 目录加载：正常目录能过，重复 ruleId 必须失败。 */
class CatalogLoaderTest {

    @Test
    void productionCatalogLoads() {
        assertDoesNotThrow(() -> CatalogLoader.loadDefault(DraftMode.SHADOW));
    }

    @Test
    void duplicateRuleIdFails() {
        assertThrows(CatalogException.class, () -> CatalogLoader.load("/safety/invalid-duplicate-id.json", DraftMode.SHADOW));
    }

    @Test
    void enforceAllowedOnTestProfile() {
        assertDoesNotThrow(() -> CatalogLoader.assertDraftModeAllowed(DraftMode.ENFORCE, new String[] {"test"}));
    }
}
