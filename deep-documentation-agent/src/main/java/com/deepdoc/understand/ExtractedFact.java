package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;

/**
 * One checkable claim about a Java file: "OrderService declares method create at line 8",
 * "OrderService references Order", and so on.
 *
 * <p>{@code subject} is usually the type the fact is about. {@code object} is the other
 * name (or the Javadoc sentence). {@code line} is 1-based in the source file.
 */
public final class ExtractedFact {
    private final FileInfo file;
    private final FactKind kind;
    private final String subject;
    private final String object;
    private final int line;

    public ExtractedFact(FileInfo file, FactKind kind, String subject, String object, int line) {
        this.file = file;
        this.kind = kind;
        this.subject = subject == null ? "" : subject;
        this.object = object == null ? "" : object;
        this.line = line;
    }

    public FileInfo getFile() {
        return file;
    }

    public FactKind getKind() {
        return kind;
    }

    public String getSubject() {
        return subject;
    }

    public String getObject() {
        return object;
    }

    public int getLine() {
        return line;
    }

    /** True when this fact mentions another type name that might live in a different file. */
    public boolean isMention() {
        return kind == FactKind.REFERENCES || kind == FactKind.EXTENDS
                || kind == FactKind.IMPLEMENTS || kind == FactKind.IMPORTS;
    }

    public String mentionName() {
        if (kind == FactKind.IMPORTS) {
            int dot = object.lastIndexOf('.');
            return dot < 0 ? object : object.substring(dot + 1);
        }
        if (kind == FactKind.REFERENCES || kind == FactKind.EXTENDS || kind == FactKind.IMPLEMENTS) {
            return object;
        }
        return "";
    }

    @Override
    public String toString() {
        if (object.isEmpty()) {
            return kind + " " + subject;
        }
        return kind + " " + subject + " → " + object;
    }
}
