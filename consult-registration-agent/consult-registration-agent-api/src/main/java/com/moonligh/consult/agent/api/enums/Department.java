package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 推荐科室。只包含当前账号已开通的 10 个极速问诊科室。
 * JSON 与数据库使用 code：1 内科，2 外科，3 男科，4 全科，5 骨科，6 妇产科，7 口腔科，8 儿科，9 耳鼻喉科，10 中医科。
 */
public enum Department {

    INTERNAL(1, "内科"),
    SURGERY(2, "外科"),
    ANDROLOGY(3, "男科"),
    GENERAL(4, "全科"),
    ORTHOPEDICS(5, "骨科"),
    OBGYN(6, "妇产科"),
    STOMATOLOGY(7, "口腔科"),
    PEDIATRICS(8, "儿科"),
    ENT(9, "耳鼻喉科"),
    TCM(10, "中医科");

    /** 对外传递的数字。 */
    private final int code;
    /** 中文名称，给页面展示，不进 JSON。 */
    private final String label;

    Department(int code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** JSON 和数据库里的数字转成枚举。对不上就报错。 */
    @JsonCreator
    public static Department fromCode(int code) {
        for (Department item : Department.values()) {
            if (item.code == code) {
                return item;
            }
        }
        throw new IllegalArgumentException("未知的科室 code: " + code);
    }
}
