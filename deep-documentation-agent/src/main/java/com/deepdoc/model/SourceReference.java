package com.deepdoc.model;

import java.nio.file.Path;

/**
 * A location in the source that backs a claim, e.g. the import line behind a dependency edge.
 * Generated documentation cites these so that every statement about the code can be checked.
 * Lines are 1-based and inclusive.
 */
public final class SourceReference {
    private final Path file;
    private final int startLine;
    private final int endLine;
    private final String snippet;

    public SourceReference(Path file, int startLine, int endLine, String snippet) {
        this.file = file;
        this.startLine = startLine;
        this.endLine = endLine;
        this.snippet = snippet;
    }

    /** Reference to a single line, without a snippet. */
    public static SourceReference line(Path file, int line) {
        return new SourceReference(file, line, line, null);
    }

    public Path getFile() {
        return file;
    }

    public int getStartLine() {
        return startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    /** Quoted source text, or null when only the location is known. */
    public String getSnippet() {
        return snippet;
    }

    @Override
    public String toString() {
        return file.getFileName() + ":" + startLine;
    }
}
