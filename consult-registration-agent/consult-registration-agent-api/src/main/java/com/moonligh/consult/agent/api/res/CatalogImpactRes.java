package com.moonligh.consult.agent.api.res;

/**
 * 停用前的影响面。在途数为近似。
 */
public record CatalogImpactRes(
        int cardReady,
        int confirmedUnpaid,
        int doctors,
        int onDutyToday,
        boolean hidesParent,
        String warning) {
}
