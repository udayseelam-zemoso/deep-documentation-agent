package com.deepdoc.config;

/**
 * The {@code understand} section of {@code application.yml}: which ranked files are parsed
 * into outlines for the knowledge graph.
 */
public class UnderstandSection {
    /** LISTED files are many and rarely documented in depth, so they are skipped by default. */
    private boolean includeListed = false;
    /** Cap so a 10k-file repo does not pay for an AST walk of everything. */
    private int maxFiles = 40;

    public boolean isIncludeListed() {
        return includeListed;
    }

    public void setIncludeListed(boolean includeListed) {
        this.includeListed = includeListed;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }
}
