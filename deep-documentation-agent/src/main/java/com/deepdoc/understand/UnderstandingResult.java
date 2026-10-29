package com.deepdoc.understand;

import java.util.List;

/** Output of {@link JavaUnderstandingService}: outlines, facts, and the type-use graph. */
public final class UnderstandingResult {
    private final KnowledgeGraph knowledge;
    private final int astFiles;
    private final int skippedNonJava;
    private final int failedFiles;

    public UnderstandingResult(KnowledgeGraph knowledge, int astFiles, int skippedNonJava, int failedFiles) {
        this.knowledge = knowledge;
        this.astFiles = astFiles;
        this.skippedNonJava = skippedNonJava;
        this.failedFiles = failedFiles;
    }

    public KnowledgeGraph getKnowledge() {
        return knowledge;
    }

    public List<FileOutline> getOutlines() {
        return knowledge.getOutlines();
    }

    public int getAstFiles() {
        return astFiles;
    }

    public int getSkippedNonJava() {
        return skippedNonJava;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public int getFactCount() {
        return knowledge.getFacts().size();
    }
}
