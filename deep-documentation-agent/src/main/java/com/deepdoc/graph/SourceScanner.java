package com.deepdoc.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight lexical helpers shared by all dependency extractors. Not a parser: good enough to
 * find import-like directives and count identifier usage without matching text inside comments
 * or string literals. Blanking keeps newlines and character offsets intact so line numbers and
 * regex offsets stay valid.
 */
public final class SourceScanner {

    public enum Syntax {
        /** Java, Kotlin, Scala, C#, C/C++: // and block comments, '...' and "..." and """...""". */
        C_LIKE(true, false, true, true, false, false),
        JS(true, false, true, false, true, false),
        GO(true, false, true, false, true, false),
        /** Single quotes are lifetimes as often as char literals, so only 'x' and '\x' count. */
        RUST(true, false, false, false, false, true),
        SWIFT(true, false, false, true, false, false),
        PYTHON(false, true, true, true, false, false),
        RUBY(false, true, true, false, false, false),
        SHELL(false, true, true, false, false, false),
        PHP(true, true, true, false, false, false);

        final boolean slashComments;
        final boolean hashComments;
        final boolean singleQuoteStrings;
        final boolean tripleQuotes;
        final boolean backtickStrings;
        final boolean strictCharLiterals;

        Syntax(boolean slashComments, boolean hashComments, boolean singleQuoteStrings,
               boolean tripleQuotes, boolean backtickStrings, boolean strictCharLiterals) {
            this.slashComments = slashComments;
            this.hashComments = hashComments;
            this.singleQuoteStrings = singleQuoteStrings;
            this.tripleQuotes = tripleQuotes;
            this.backtickStrings = backtickStrings;
            this.strictCharLiterals = strictCharLiterals;
        }
    }

    private SourceScanner() {
    }

    /** Blanks comments and string literals. */
    public static String blankCommentsAndLiterals(String src, Syntax syntax) {
        return blank(src, syntax, true);
    }

    /** Blanks comments only; string literals (import paths, include names) are kept. */
    public static String blankComments(String src, Syntax syntax) {
        return blank(src, syntax, false);
    }

    private static String blank(String src, Syntax syntax, boolean blankStrings) {
        StringBuilder out = new StringBuilder(src);
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            int end;
            if (syntax.slashComments && c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                end = lineEnd(src, i);
                fill(out, i, end);
            } else if (syntax.slashComments && c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                fill(out, i, end);
            } else if (syntax.hashComments && c == '#' && isHashComment(src, i, syntax)) {
                end = lineEnd(src, i);
                fill(out, i, end);
            } else if (syntax.tripleQuotes && (src.startsWith("\"\"\"", i) || (syntax == Syntax.PYTHON && src.startsWith("'''", i)))) {
                String quote = src.substring(i, i + 3);
                end = src.indexOf(quote, i + 3);
                end = end < 0 ? n : end + 3;
                if (blankStrings) fill(out, i, end);
            } else if (c == '"' || (c == '\'' && syntax.singleQuoteStrings) || (c == '`' && syntax.backtickStrings)) {
                end = stringEnd(src, i, c, c == '`');
                if (blankStrings) fill(out, i, end);
            } else if (c == '\'' && syntax.strictCharLiterals && isStrictCharLiteral(src, i)) {
                end = src.indexOf('\'', i + 2) + 1;
                if (blankStrings) fill(out, i, end);
            } else {
                end = i + 1;
            }
            i = end;
        }
        return out.toString();
    }

    private static boolean isHashComment(String src, int i, Syntax syntax) {
        if (syntax == Syntax.SHELL && i > 0) {
            char prev = src.charAt(i - 1);
            // $# and ${#var} are not comments
            return prev != '$' && prev != '{' && (Character.isWhitespace(prev) || prev == ';');
        }
        // PHP 8 attributes start with #[
        return !(syntax == Syntax.PHP && i + 1 < src.length() && src.charAt(i + 1) == '[');
    }

    private static boolean isStrictCharLiteral(String src, int i) {
        if (i + 2 < src.length() && src.charAt(i + 1) != '\\' && src.charAt(i + 2) == '\'') return true;
        if (i + 1 < src.length() && src.charAt(i + 1) == '\\') {
            int close = src.indexOf('\'', i + 2);
            return close > 0 && close - i <= 10 && src.indexOf('\n', i) > close;
        }
        return false;
    }

    private static int lineEnd(String src, int from) {
        int end = src.indexOf('\n', from);
        return end < 0 ? src.length() : end;
    }

    private static int stringEnd(String src, int start, char quote, boolean multiline) {
        int j = start + 1;
        int n = src.length();
        while (j < n && src.charAt(j) != quote && (multiline || src.charAt(j) != '\n')) {
            if (src.charAt(j) == '\\') j++;
            j++;
        }
        return Math.min(n, j + 1);
    }

    private static void fill(StringBuilder out, int from, int to) {
        for (int k = from; k < to; k++) {
            if (out.charAt(k) != '\n') {
                out.setCharAt(k, ' ');
            }
        }
    }

    /** Blanks whole lines whose first word is one of {@code keywords} (e.g. package, import). */
    static String blankLinesStartingWith(String text, String... keywords) {
        StringBuilder out = new StringBuilder(text);
        int lineStart = 0;
        int n = text.length();
        while (lineStart < n) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = n;
            String trimmed = text.substring(lineStart, lineEnd).trim();
            for (String keyword : keywords) {
                if (trimmed.startsWith(keyword + " ") || trimmed.equals(keyword)) {
                    fill(out, lineStart, lineEnd);
                    break;
                }
            }
            lineStart = lineEnd + 1;
        }
        return out.toString();
    }

    /** Blanks the given character range. */
    static void blankRange(StringBuilder text, int from, int to) {
        fill(text, from, to);
    }

    /** Identifier -> {occurrences, first line}. */
    static Map<String, int[]> countIdentifiers(String text) {
        Map<String, int[]> counts = new HashMap<>();
        int line = 1;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                i++;
            } else if (Character.isJavaIdentifierStart(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(text.charAt(j))) j++;
                String id = text.substring(i, j);
                int[] entry = counts.get(id);
                if (entry == null) {
                    counts.put(id, new int[]{1, line});
                } else {
                    entry[0]++;
                }
                i = j;
            } else if (Character.isDigit(c)) {
                int j = i + 1;
                while (j < n && Character.isJavaIdentifierPart(text.charAt(j))) j++;
                i = j;
            } else {
                i++;
            }
        }
        return counts;
    }

    static int count(Map<String, int[]> tokens, String identifier) {
        int[] entry = tokens.get(identifier);
        return entry == null ? 0 : entry[0];
    }

    static int firstLine(Map<String, int[]> tokens, String identifier) {
        int[] entry = tokens.get(identifier);
        return entry == null ? 1 : entry[1];
    }
}
