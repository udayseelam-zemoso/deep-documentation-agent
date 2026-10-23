package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

/** One dependency found by an extractor, before it is merged into the graph. */
final class RawDependency {
    final FileInfo from;
    final FileInfo to;
    final int referenceCount;
    /** Line of the first reference in {@code from}; 0 when there is no source evidence. */
    final int line;
    /** True when inferred from Git co-changes rather than seen in the code. */
    final boolean coChange;

    RawDependency(FileInfo from, FileInfo to, int referenceCount, int line) {
        this(from, to, referenceCount, line, false);
    }

    private RawDependency(FileInfo from, FileInfo to, int referenceCount, int line, boolean coChange) {
        this.from = from;
        this.to = to;
        this.referenceCount = referenceCount;
        this.line = line;
        this.coChange = coChange;
    }

    static RawDependency coChange(FileInfo from, FileInfo to) {
        return new RawDependency(from, to, 1, 0, true);
    }
}
