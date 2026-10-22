package com.deepdoc.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * Lightweight lexical helpers for Java source. Not a parser: good enough to find
 * package/import directives and count identifier usage without matching text
 * inside comments or string literals.
 */
public final class JavaSourceScanner {
    private JavaSourceScanner() {
    }

    /** Replaces comments and string/char/text-block literals with spaces, keeping newlines and offsets. */
    public static String blankCommentsAndLiterals(String src) {
        StringBuilder out = new StringBuilder(src);
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                int end = src.indexOf('\n', i);
                if (end < 0) end = n;
                blank(out, i, end);
                i = end;
            } else if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                blank(out, i, end);
                i = end;
            } else if (c == '"' && src.startsWith("\"\"\"", i)) {
                int end = src.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                blank(out, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && src.charAt(j) != c && src.charAt(j) != '\n') {
                    if (src.charAt(j) == '\\') j++;
                    j++;
                }
                int end = Math.min(n, j + 1);
                blank(out, i, end);
                i = end;
            } else {
                i++;
            }
        }
        return out.toString();
    }

    private static void blank(StringBuilder out, int from, int to) {
        for (int k = from; k < to; k++) {
            if (out.charAt(k) != '\n') {
                out.setCharAt(k, ' ');
            }
        }
    }

    /** Blanks {@code package ...;} and {@code import ...;} lines so they do not count as usages. */
    static String blankDirectives(String cleaned) {
        StringBuilder out = new StringBuilder(cleaned);
        int lineStart = 0;
        int n = cleaned.length();
        while (lineStart < n) {
            int lineEnd = cleaned.indexOf('\n', lineStart);
            if (lineEnd < 0) lineEnd = n;
            String trimmed = cleaned.substring(lineStart, lineEnd).trim();
            if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) {
                blank(out, lineStart, lineEnd);
            }
            lineStart = lineEnd + 1;
        }
        return out.toString();
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
}
