package com.deepdoc.graph;

import java.util.Arrays;

/** Maps character offsets to 1-based line numbers. */
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

    int lineOf(int offset) {
        int idx = Arrays.binarySearch(lineStarts, offset);
        return (idx >= 0 ? idx : -idx - 2) + 1;
    }
}
