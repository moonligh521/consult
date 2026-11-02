package com.moonligh.consult.agent.domain.department;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 拼音、英文、方言、未进目录的口误 → 目录词。只给主诉模型槽用，不进 {@link com.moonligh.consult.agent.domain.SpokenNormalizer}。
 */
public final class InformalLexicon {

    public static final String LOCATION = "/department/informal-lexicon.json";

    private static final InformalLexicon CACHED = read();
    private static final Pattern NEGATED_BEFORE = Pattern.compile(
            "(没有|并非|不是|否认|不算|没|无|未)[^，。；,;.!?]{0,6}$");
    private static final Pattern HISTORY_BEFORE = Pattern.compile(
            "(既往|以前|曾经|之前|老毛病|一直有)[^，。；,;.!?]{0,8}$");
    private static final Pattern HISTORY_AFTER = Pattern.compile(
            "^[^，。；,;.!?]{0,3}(病史|多年|好几年|十几年|吃药控制)");
    private static final Pattern FAMILY_BEFORE = Pattern.compile(
            "(我妈|我爸|妈妈|爸爸|母亲|父亲|家人|家族)[^，。；,;.!?]{0,4}$");

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
            if (longestAt(original, view, index) != null) {
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
            Entry best = longestAt(original, view, index);
            if (best == null) {
                index++;
                continue;
            }
            int end = index + best.from().length();
            int origStart = view.origStart[index];
            int origEnd = view.origEnd[end - 1];
            if (!unsafeContext(original, origStart, origEnd)) {
                hits.add(new Hit(origStart, origEnd, original.substring(origStart, origEnd), best.to()));
            }
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
        List<Match> matches = hits.stream()
                .map(hit -> new Match(hit.surface(), hit.to(), hit.origStart(), hit.origEnd()))
                .toList();
        return new Rewrite(rewritten.toString(), matches);
    }

    private Entry longestAt(String original, CompactView view, int index) {
        Entry best = null;
        for (Entry entry : entries) {
            String from = entry.from();
            if (from.isEmpty() || index + from.length() > view.compact.length()) {
                continue;
            }
            if (!view.compact.startsWith(from, index)) {
                continue;
            }
            int end = index + from.length();
            if (asciiWord(from)
                    && !asciiBoundaries(original, view.origStart[index], view.origEnd[end - 1])) {
                continue;
            }
            if (best == null || from.length() > best.from().length()) {
                best = entry;
            }
        }
        return best;
    }

    private static boolean asciiWord(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch > 127 || !Character.isLetterOrDigit(ch)) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiBoundaries(String original, int start, int end) {
        int before = start > 0 ? original.codePointBefore(start) : -1;
        int after = end < original.length() ? original.codePointAt(end) : -1;
        return !asciiLetterOrDigit(before) && !asciiLetterOrDigit(after);
    }

    private static boolean asciiLetterOrDigit(int codePoint) {
        if (codePoint < 0) {
            return false;
        }
        String normalized = Normalizer.normalize(
                new String(Character.toChars(codePoint)), Normalizer.Form.NFKC);
        return !normalized.isEmpty()
                && normalized.codePoints().allMatch(value ->
                        value <= 127 && Character.isLetterOrDigit(value));
    }

    private static boolean unsafeContext(String original, int start, int end) {
        String before = original.substring(Math.max(0, start - 16), start);
        String after = original.substring(end, Math.min(original.length(), end + 12));
        return NEGATED_BEFORE.matcher(afterLastContrast(before)).find()
                || HISTORY_BEFORE.matcher(before).find()
                || HISTORY_AFTER.matcher(after).find()
                || FAMILY_BEFORE.matcher(before).find();
    }

    private static String afterLastContrast(String text) {
        int boundary = 0;
        for (String marker : List.of("但是", "但有", "不过", "可是", "然而", "现在有", "目前有", "后来有", "但")) {
            int at = text.lastIndexOf(marker);
            if (at >= 0) {
                boundary = Math.max(boundary, at + marker.length());
            }
        }
        return text.substring(boundary);
    }

    public record Entry(String from, String to) {
        public Entry {
            from = from == null ? "" : from;
            to = to == null ? "" : to;
        }
    }

    public record Match(String surface, String target, int start, int end) {
    }

    public record Rewrite(String rewritten, List<Match> matches) {
        static final Rewrite NONE = new Rewrite("", List.of());

        public Rewrite {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }

        public boolean hit() {
            return rewritten != null && !rewritten.isEmpty();
        }

        public String evidence() {
            return matches.isEmpty() ? "" : matches.get(0).surface();
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
