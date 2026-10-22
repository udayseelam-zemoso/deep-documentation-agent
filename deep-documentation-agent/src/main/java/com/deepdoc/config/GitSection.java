package com.deepdoc.config;

public class GitSection {
    private boolean enabled = true;
    private int historyMonths = 24;
    private double halfLifeDays = 180;
    private int maxCommits = 5000;
    private int bulkCommitFileLimit = 50;
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
