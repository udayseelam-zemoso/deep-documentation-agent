package com.deepdoc.config;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code analysis} section of {@code application.yml}: which files to skip while walking
 * the repository, and how sure a YAML architecture pattern must be before it is reported.
 */
public class AnalysisSection {
    private List<String> ignoreDirectories = new ArrayList<>();
    private List<String> ignoreExtensions = new ArrayList<>();
    /** A pattern in architectures.yml is kept only when matched-weight / total-weight is at least this. */
    private double architectureMinConfidence = 0.3;

    public List<String> getIgnoreDirectories() {
        return ignoreDirectories;
    }

    public void setIgnoreDirectories(List<String> ignoreDirectories) {
        this.ignoreDirectories = ignoreDirectories != null ? ignoreDirectories : new ArrayList<>();
    }

    public List<String> getIgnoreExtensions() {
        return ignoreExtensions;
    }

    public void setIgnoreExtensions(List<String> ignoreExtensions) {
        this.ignoreExtensions = ignoreExtensions != null ? ignoreExtensions : new ArrayList<>();
    }

    public double getArchitectureMinConfidence() {
        return architectureMinConfidence;
    }

    public void setArchitectureMinConfidence(double architectureMinConfidence) {
        this.architectureMinConfidence = architectureMinConfidence;
    }
}
