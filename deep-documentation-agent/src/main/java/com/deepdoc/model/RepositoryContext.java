package com.deepdoc.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepositoryContext {
    private String detectedArchitecture;
    private final List<String> frameworks;
    private final Map<String, String> technologies;
    private boolean hasGitHistory;

    public RepositoryContext() {
        this.frameworks = new ArrayList<>();
        this.technologies = new HashMap<>();
        this.detectedArchitecture = "Unknown";
        this.hasGitHistory = false;
    }

    public String getDetectedArchitecture() {
        return detectedArchitecture;
    }

    public void setDetectedArchitecture(String detectedArchitecture) {
        this.detectedArchitecture = detectedArchitecture;
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
        technologies.put(category, technology);
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
                ", frameworks=" + frameworks +
                ", technologies=" + technologies +
                ", hasGitHistory=" + hasGitHistory +
                '}';
    }
}
