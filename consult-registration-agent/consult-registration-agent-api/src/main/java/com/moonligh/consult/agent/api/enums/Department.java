package com.moonligh.consult.agent.api.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 推荐科室。一级科 22 个，编号启用后不要改、不要复用。
 * 1–10 保持原号；11–22 为后来对齐的一级科。
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
    ENT(9, "耳鼻咽喉头颈外科"),
    TCM(10, "中医科"),
    DERMATOLOGY(11, "皮肤性病科"),
    INFECTIOUS(12, "感染病科"),
    OPHTHALMOLOGY(13, "眼科"),
    PSYCHIATRY(14, "精神心理科"),
    PLASTIC(15, "整形美容科"),
    NUTRITION(16, "营养科"),
    ONCOLOGY(17, "肿瘤科"),
    REPRODUCTIVE(18, "生殖医学中心"),
    ANESTHESIA(19, "麻醉医学科"),
    SLEEP(20, "睡眠医学科"),
    INTEGRATIVE(21, "中西医结合科"),
    REHAB(22, "康复医学科");

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

    /** 种子枚举。目录里后加的一级没有对应常量，返回 null。 */
    public static Department tryFromCode(int code) {
        for (Department item : Department.values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }

    /** JSON 和数据库里的数字。未知码返回 null，以目录行为准。 */
    @JsonCreator
    public static Department fromCode(int code) {
        return tryFromCode(code);
    }
}
