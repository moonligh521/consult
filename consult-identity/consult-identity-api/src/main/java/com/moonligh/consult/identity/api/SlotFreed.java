package com.moonligh.consult.identity.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 号源空出。挂号按二级科和档位弹出队头。Redis Stream 只在适配层收这组字段。
 */
public record SlotFreed(String specialty, int grade) {

    public Map<String, String> streamFields() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("specialty", specialty == null ? "" : specialty);
        body.put("grade", Integer.toString(grade));
        return body;
    }

    /** 字段缺失或档位不是整数时返回 null，调用方直接确认这条消息。 */
    public static SlotFreed fromStream(Map<String, String> body) {
        if (body == null) {
            return null;
        }
        String specialty = body.get("specialty") == null ? "" : body.get("specialty");
        String rawGrade = body.get("grade");
        try {
            return new SlotFreed(specialty, Integer.parseInt(rawGrade == null ? "" : rawGrade));
        } catch (NumberFormatException error) {
            return null;
        }
    }
}
