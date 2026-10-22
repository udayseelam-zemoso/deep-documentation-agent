package com.deepdoc.config;

import java.util.ArrayList;
import java.util.List;

public class AnalysisSection {
    private List<String> ignoreDirectories = new ArrayList<>();
    private List<String> ignoreExtensions = new ArrayList<>();

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
}
