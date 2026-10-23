package com.deepdoc.config;

/**
 * The {@code git} section of {@code application.yml}: how much history is read and how it is
 * weighted into the activity signal ({@code com.deepdoc.ranker.ActivityScores}).
 */
public class GitSection {
    /** Set to false to rank on structure only, even when the repository has history. */
    private boolean enabled = true;
    /** Only commits from this many months back are read. */
    private int historyMonths = 24;
    /** A commit this many days old counts half as much as one made today. */
    private double halfLifeDays = 180;
    /** Upper bound on commits read, to keep analysis fast on very long histories. */
    private int maxCommits = 5000;
    /** Commits touching more files than this (reformatting, renames, vendoring) are ignored. */
    private int bulkCommitFileLimit = 50;
    /** Recent commit messages kept for later LLM context. */
    private int maxCommitMessages = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getHistoryMonths() {
        return historyMonths;
    }

    public void setHistoryMonths(int historyMonths) {
        this.historyMonths = historyMonths;
    }

    public double getHalfLifeDays() {
        return halfLifeDays;
    }

    public void setHalfLifeDays(double halfLifeDays) {
        this.halfLifeDays = halfLifeDays;
    }

    public int getMaxCommits() {
        return maxCommits;
    }

    public void setMaxCommits(int maxCommits) {
        this.maxCommits = maxCommits;
    }

    public int getBulkCommitFileLimit() {
        return bulkCommitFileLimit;
    }

    public void setBulkCommitFileLimit(int bulkCommitFileLimit) {
        this.bulkCommitFileLimit = bulkCommitFileLimit;
    }

    public int getMaxCommitMessages() {
        return maxCommitMessages;
    }

    public void setMaxCommitMessages(int maxCommitMessages) {
        this.maxCommitMessages = maxCommitMessages;
    }
}
