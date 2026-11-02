package com.moonligh.consult.framework.observability;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 永久丢掉姓名、手机、证件、Authorization 和模型密钥，防止误写进 span。
 */
public final class SensitiveAttributeFilter {

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(bearer\\s+[a-z0-9._\\-]+|sk-[a-z0-9]{8,}|api[_-]?key\\s*[:=])");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
    private static final Pattern ID_CARD = Pattern.compile("(?i)\\b\\d{17}[0-9x]\\b");

    private SensitiveAttributeFilter() {
    }

    public static boolean blockedKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replace("-", "_");
        return normalized.contains("authorization")
                || normalized.contains("api_key")
                || normalized.contains("apikey")
                || normalized.contains("password")
                || normalized.contains("secret")
                || normalized.contains("mobile")
                || normalized.contains("phone")
                || normalized.contains("id_card")
                || normalized.contains("idcard")
                || normalized.contains("id_no")
                || normalized.contains("patient_name")
                || normalized.contains("patientname")
                || normalized.equals("name")
                || normalized.equals("username");
    }

    public static boolean blockedValue(Object value) {
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return SECRET.matcher(text).find()
                || MOBILE.matcher(text).find()
                || ID_CARD.matcher(text).find();
    }

    public static boolean allow(String key, Object value) {
        return !blockedKey(key) && !blockedValue(value);
    }
}
