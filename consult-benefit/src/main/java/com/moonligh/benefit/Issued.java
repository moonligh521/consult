package com.moonligh.benefit;

/** 发给某个账号的一张卡。 */
public record Issued(String cardId, String accountId, String productCode) {
}
