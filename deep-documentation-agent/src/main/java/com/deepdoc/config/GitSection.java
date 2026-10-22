package com.deepdoc.config;

public class GitSection {
    private boolean enabled = true;
    private int churnAnalysisMonths = 6;
    private int maxCommitMessages = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getChurnAnalysisMonths() {
        return churnAnalysisMonths;
    }

    public void setChurnAnalysisMonths(int churnAnalysisMonths) {
        this.churnAnalysisMonths = churnAnalysisMonths;
    }

    public int getMaxCommitMessages() {
        return maxCommitMessages;
    }

    public void setMaxCommitMessages(int maxCommitMessages) {
        this.maxCommitMessages = maxCommitMessages;
    }
}
