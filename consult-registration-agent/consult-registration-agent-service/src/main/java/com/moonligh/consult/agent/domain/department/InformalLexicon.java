package com.moonligh.consult.agent.domain.department;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 拼音、英文、方言、未进目录的口误 → 目录词。只给主诉模型槽用，不进 {@link com.moonligh.consult.agent.domain.SpokenNormalizer}。
 */
public final class InformalLexicon {

    public static final String LOCATION = "/department/informal-lexicon.json";

    private static final InformalLexicon CACHED = read();

    private final List<Entry> entries;

    InformalLexicon(List<Entry> entries) {
        List<Entry> copy = new ArrayList<>(entries == null ? List.of() : entries);
        copy.sort(Comparator.comparingInt((Entry item) -> item.from().length()).reversed()
                .thenComparing(Entry::from));
        this.entries = List.copyOf(copy);
    }

    public static InformalLexicon load() {
        return CACHED;
    }

    private static InformalLexicon read() {
        try (InputStream in = InformalLexicon.class.getResourceAsStream(LOCATION)) {
            if (in == null) {
                throw new IllegalStateException("找不到口语词表 " + LOCATION);
            }
            File file = new ObjectMapper().findAndRegisterModules().readValue(in, File.class);
            return new InformalLexicon(file.entries());
        } catch (IOException error) {
            throw new IllegalStateException("读取口语词表失败", error);
        }
    }

    /** 原话能否被词表解出至少一处。短中文口误据此放行进模型，避免 TOO_SHORT。 */
    public boolean matches(String original) {
        if (original == null || original.isEmpty() || entries.isEmpty()) {
            return false;
        }
        CompactView view = CompactView.of(original);
        int index = 0;
        while (index < view.compact.length()) {
            if (longestAt(view.compact, index) != null) {
                return true;
            }
            index++;
        }
        return false;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** 把原话里的口语形式换成目录词。没有任何命中则 rewritten 为空。 */
    public Rewrite rewrite(String original) {
        if (original == null || original.isEmpty() || entries.isEmpty()) {
            return Rewrite.NONE;
        }
        CompactView view = CompactView.of(original);
        if (view.compact.isEmpty()) {
            return Rewrite.NONE;
        }
        List<Hit> hits = new ArrayList<>();
        int index = 0;
        while (index < view.compact.length()) {
            Entry best = longestAt(view.compact, index);
            if (best == null) {
                index++;
                continue;
            }
            int end = index + best.from().length();
            int origStart = view.origStart[index];
            int origEnd = view.origEnd[end - 1];
            hits.add(new Hit(origStart, origEnd, original.substring(origStart, origEnd), best.to()));
            index = end;
        }
        if (hits.isEmpty()) {
            return Rewrite.NONE;
        }
        StringBuilder rewritten = new StringBuilder(original);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            rewritten.replace(hit.origStart(), hit.origEnd(), hit.to());
        }
        return new Rewrite(rewritten.toString(), hits.get(0).surface());
    }

    private Entry longestAt(String compact, int index) {
        Entry best = null;
        for (Entry entry : entries) {
            String from = entry.from();
            if (from.isEmpty() || index + from.length() > compact.length()) {
                continue;
            }
            if (!compact.startsWith(from, index)) {
                continue;
            }
            if (best == null || from.length() > best.from().length()) {
                best = entry;
            }
        }
        return best;
    }

    public record Entry(String from, String to) {
        public Entry {
            from = from == null ? "" : from;
            to = to == null ? "" : to;
        }
    }

    public record Rewrite(String rewritten, String evidence) {
        static final Rewrite NONE = new Rewrite("", "");

        public boolean hit() {
            return rewritten != null && !rewritten.isEmpty();
        }
    }

    private record Hit(int origStart, int origEnd, String surface, String to) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record File(String version, List<Entry> entries) {
    }

    /** 紧凑串与原话下标对齐：去空白、转小写，NFKC 展开仍指向原字符。 */
    private static final class CompactView {
        private final String compact;
        private final int[] origStart;
        private final int[] origEnd;

        private CompactView(String compact, int[] origStart, int[] origEnd) {
            this.compact = compact;
            this.origStart = origStart;
            this.origEnd = origEnd;
        }

        static CompactView of(String original) {
            String src = original == null ? "" : original;
            StringBuilder compact = new StringBuilder();
            List<Integer> starts = new ArrayList<>();
            List<Integer> ends = new ArrayList<>();
            int index = 0;
            while (index < src.length()) {
                int code = src.codePointAt(index);
                int next = index + Character.charCount(code);
                if (!Character.isWhitespace(code)) {
                    String piece = Normalizer.normalize(src.substring(index, next), Normalizer.Form.NFKC);
                    for (int at = 0; at < piece.length(); ) {
                        int mapped = piece.codePointAt(at);
                        compact.appendCodePoint(Character.toLowerCase(mapped));
                        starts.add(index);
                        ends.add(next);
                        at += Character.charCount(mapped);
                    }
                }
                index = next;
            }
            int[] origStart = new int[starts.size()];
            int[] origEnd = new int[ends.size()];
            for (int i = 0; i < starts.size(); i++) {
                origStart[i] = starts.get(i);
                origEnd[i] = ends.get(i);
            }
            return new CompactView(compact.toString(), origStart, origEnd);
        }
    }
}
