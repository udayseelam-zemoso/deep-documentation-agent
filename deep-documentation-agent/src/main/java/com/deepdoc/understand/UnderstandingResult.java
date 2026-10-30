package com.deepdoc.understand;

import com.deepdoc.ranker.DocLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Output of {@link JavaUnderstandingService}: outlines grouped by Week 1 documentation
 * level (DEEP vs SUMMARY), facts, the type-use graph, and a one-sentence responsibility
 * per outlined file. LISTED files are not outlined unless {@code understand.includeListed}
 * is on.
 */
public final class UnderstandingResult {
    private final KnowledgeGraph knowledge;
    private final int astFiles;
    private final int lexicalFiles;
    private final int skippedListed;
    private final int failedFiles;
    /** {@code openai} when a DEEP rewrite was attempted, otherwise {@code heuristic}. */
    private final String llmMode;

    public UnderstandingResult(KnowledgeGraph knowledge, int astFiles, int lexicalFiles,
                               int skippedListed, int failedFiles, String llmMode) {
        this.knowledge = knowledge;
        this.astFiles = astFiles;
        this.lexicalFiles = lexicalFiles;
        this.skippedListed = skippedListed;
        this.failedFiles = failedFiles;
        this.llmMode = llmMode == null ? "heuristic" : llmMode;
    }

    public KnowledgeGraph getKnowledge() {
        return knowledge;
    }

    public List<FileOutline> getOutlines() {
        return knowledge.getOutlines();
    }

    /** DEEP files, still in ranking order. These get method facts and (later) a full section. */
    public List<FileOutline> deepOutlines() {
        return outlinesAt(DocLevel.DEEP);
    }

    /** SUMMARY files, still in ranking order. These get a paragraph's worth of facts. */
    public List<FileOutline> summaryOutlines() {
        return outlinesAt(DocLevel.SUMMARY);
    }

    /** LISTED files, only present when {@code understand.includeListed} is on. */
    public List<FileOutline> listedOutlines() {
        return outlinesAt(DocLevel.LISTED);
    }

    public int getAstFiles() {
        return astFiles;
    }

    /** Non-Java DEEP and SUMMARY files outlined with declaration patterns. */
    public int getLexicalFiles() {
        return lexicalFiles;
    }

    public String getLlmMode() {
        return llmMode;
    }

    /** Ranked Java files left at LISTED and therefore not parsed. */
    public int getSkippedListed() {
        return skippedListed;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public int getFactCount() {
        return knowledge.getFacts().size();
    }

    private List<FileOutline> outlinesAt(DocLevel level) {
        List<FileOutline> matched = new ArrayList<>();
        for (FileOutline outline : getOutlines()) {
            if (outline.getLevel() == level) {
                matched.add(outline);
            }
        }
        return Collections.unmodifiableList(matched);
    }
}
