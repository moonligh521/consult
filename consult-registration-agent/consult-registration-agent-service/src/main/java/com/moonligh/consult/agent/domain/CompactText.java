package com.moonligh.consult.agent.domain;

import java.text.Normalizer;

/**
 * 把患者原话收成可匹配的紧凑串：NFKC、去掉空白、转小写。
 * 安全引擎、科室路由、症状抽取共用，避免「腹 泻」和「腹泻」两套行为。
 */
public final class CompactText {

    private CompactText() {
    }

    /** 原话收成紧凑串。空输入得到空串。 */
    public static String of(String original) {
        String nfkc = Normalizer.normalize(original == null ? "" : original, Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < nfkc.length(); ) {
            int codePoint = nfkc.codePointAt(i);
            int next = i + Character.charCount(codePoint);
            if (!Character.isWhitespace(codePoint)) {
                builder.appendCodePoint(Character.toLowerCase(codePoint));
            }
            i = next;
        }
        return builder.toString();
    }
}
