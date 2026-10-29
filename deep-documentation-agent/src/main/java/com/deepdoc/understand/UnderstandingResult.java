package com.deepdoc.understand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Output of {@link CodeUnderstandingService}: outlines, the symbol graph, and how the LLM was used. */
public final class UnderstandingResult {
    private final KnowledgeGraph knowledge;
    private final int astFiles;
    private final int lexicalFiles;
    private final int failedFiles;
    private final String llmMode;

    public UnderstandingResult(KnowledgeGraph knowledge, int astFiles, int lexicalFiles,
                               int failedFiles, String llmMode) {
        this.knowledge = knowledge;
        this.astFiles = astFiles;
        this.lexicalFiles = lexicalFiles;
        this.failedFiles = failedFiles;
        this.llmMode = llmMode == null ? "disabled" : llmMode;
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

    public int getLexicalFiles() {
        return lexicalFiles;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public String getLlmMode() {
        return llmMode;
    }

    public List<FileOutline> deepOutlines() {
        List<FileOutline> deep = new ArrayList<>();
        for (FileOutline outline : getOutlines()) {
            if (outline.getLevel().name().equals("DEEP")) deep.add(outline);
        }
        return Collections.unmodifiableList(deep);
    }
}
