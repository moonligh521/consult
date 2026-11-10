package com.moonligh.benefit;

import java.util.List;

/** 卡种。发出去的卡复制当时的明细，之后改卡种不影响旧卡。 */
public record Product(String code, String name, int validDays, List<ProductItem> items) {
}
