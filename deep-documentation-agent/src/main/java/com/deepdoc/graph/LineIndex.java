package com.deepdoc.graph;

import java.util.Arrays;

/**
 * Maps character offsets to 1-based line numbers. Extractors find dependencies with regular
 * expressions over the whole file, which yield offsets; edges need line numbers as evidence.
 * Line starts are recorded once, then each lookup is a binary search.
 */
final class LineIndex {
    private final int[] lineStarts;

    LineIndex(String text) {
        int[] starts = new int[16];
        int count = 0;
        starts[count++] = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (count == starts.length) {
                    starts = Arrays.copyOf(starts, count * 2);
                }
                starts[count++] = i + 1;
            }
        }
        this.lineStarts = Arrays.copyOf(starts, count);
    }

    /** Line containing {@code offset}; a miss in the binary search points just past that line's start. */
    int lineOf(int offset) {
        int idx = Arrays.binarySearch(lineStarts, offset);
        return (idx >= 0 ? idx : -idx - 2) + 1;
    }
}
