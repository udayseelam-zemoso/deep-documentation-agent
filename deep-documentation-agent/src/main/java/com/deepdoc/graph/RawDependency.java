package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

final class RawDependency {
    final FileInfo from;
    final FileInfo to;
    final int referenceCount;
    final int line;

    RawDependency(FileInfo from, FileInfo to, int referenceCount, int line) {
        this.from = from;
        this.to = to;
        this.referenceCount = referenceCount;
        this.line = line;
    }
}
