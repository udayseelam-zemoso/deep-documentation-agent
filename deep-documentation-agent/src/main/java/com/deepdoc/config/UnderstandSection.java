package com.deepdoc.config;

/**
 * Which ranked files Day 6-7 parses into outlines. LISTED files are skipped by default;
 * there are many of them and they only need a name in a later package listing.
 */
public class UnderstandSection {
    private boolean includeListed = false;
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
