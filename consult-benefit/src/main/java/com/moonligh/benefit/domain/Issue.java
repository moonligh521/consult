package com.moonligh.benefit.domain;

/** 渠道发卡。同一渠道流水号只发一张。 */
public record Issue(String accountId, String productCode, String channel, String sourceRef) {
}
