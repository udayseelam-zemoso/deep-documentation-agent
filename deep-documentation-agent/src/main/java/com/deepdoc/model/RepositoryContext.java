package com.deepdoc.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What the repository looks like after architecture detection: a label, a confidence, a
 * deployment style, the frameworks and tools that fired, and the build modules.
 */
public class RepositoryContext {
    private String detectedArchitecture;
    private double architectureConfidence;
    private ArchitectureStyle architectureStyle;
    private final List<String> frameworks;
    private final Map<String, String> technologies;
    private List<DetectedPattern> patterns;
    private List<DetectedModule> modules;
    private boolean hasGitHistory;

    public RepositoryContext() {
        this.frameworks = new ArrayList<>();
        this.technologies = new HashMap<>();
        this.patterns = new ArrayList<>();
        this.modules = new ArrayList<>();
        this.detectedArchitecture = "Unknown";
        this.architectureConfidence = 0;
        this.architectureStyle = ArchitectureStyle.UNKNOWN;
        this.hasGitHistory = false;
    }

    public String getDetectedArchitecture() {
        return detectedArchitecture;
    }

    public void setDetectedArchitecture(String detectedArchitecture) {
        this.detectedArchitecture = detectedArchitecture;
    }

    /** 0..1; 0 when the architecture is Unknown. */
    public double getArchitectureConfidence() {
        return architectureConfidence;
    }

    public void setArchitectureConfidence(double architectureConfidence) {
        this.architectureConfidence = architectureConfidence;
    }

    public ArchitectureStyle getArchitectureStyle() {
        return architectureStyle;
    }

    public void setArchitectureStyle(ArchitectureStyle architectureStyle) {
        this.architectureStyle = architectureStyle == null ? ArchitectureStyle.UNKNOWN : architectureStyle;
    }

    public List<DetectedPattern> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<DetectedPattern> patterns) {
        this.patterns = patterns == null ? new ArrayList<>() : patterns;
    }

    public List<DetectedModule> getModules() {
        return modules;
    }

    public void setModules(List<DetectedModule> modules) {
        this.modules = modules == null ? new ArrayList<>() : modules;
    }

    public List<String> getFrameworks() {
        return frameworks;
    }

    public void addFramework(String framework) {
        if (!frameworks.contains(framework)) {
            frameworks.add(framework);
        }
    }

    public Map<String, String> getTechnologies() {
        return technologies;
    }

    public void addTechnology(String category, String technology) {
        String existing = technologies.get(category);
        if (existing == null) {
            technologies.put(category, technology);
            return;
        }
        for (String part : existing.split(", ")) {
            if (part.equals(technology)) return;
        }
        technologies.put(category, existing + ", " + technology);
    }

    public boolean hasGitHistory() {
        return hasGitHistory;
    }

    public void setHasGitHistory(boolean hasGitHistory) {
        this.hasGitHistory = hasGitHistory;
    }

    @Override
    public String toString() {
        return "RepositoryContext{" +
                "architecture='" + detectedArchitecture + '\'' +
                ", confidence=" + architectureConfidence +
                ", style=" + architectureStyle +
                ", frameworks=" + frameworks +
                ", technologies=" + technologies +
                ", modules=" + modules.size() +
                ", hasGitHistory=" + hasGitHistory +
                '}';
    }
}
