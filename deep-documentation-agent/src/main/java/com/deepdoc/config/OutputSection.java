package com.deepdoc.config;

public class OutputSection {
    private String defaultDirectory = "docs";
    private String format = "markdown";
    private boolean generateDiagrams = true;

    public String getDefaultDirectory() {
        return defaultDirectory;
    }

    public void setDefaultDirectory(String defaultDirectory) {
        this.defaultDirectory = defaultDirectory;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public boolean isGenerateDiagrams() {
        return generateDiagrams;
    }

    public void setGenerateDiagrams(boolean generateDiagrams) {
        this.generateDiagrams = generateDiagrams;
    }
}
