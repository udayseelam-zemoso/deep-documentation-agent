package com.deepdoc.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One entry of {@code languages.yml}. Setter-based so SnakeYAML can bind it.
 */
public class LanguageDefinition {
    private String name;
    private String type = "programming";
    private List<String> extensions = new ArrayList<>();
    private List<String> filenames = new ArrayList<>();
    private List<String> interpreters = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LanguageType languageType() {
        return LanguageType.valueOf(type.trim().toUpperCase(Locale.ROOT));
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<String> extensions) {
        this.extensions = extensions == null ? new ArrayList<>() : extensions;
    }

    public List<String> getFilenames() {
        return filenames;
    }

    public void setFilenames(List<String> filenames) {
        this.filenames = filenames == null ? new ArrayList<>() : filenames;
    }

    public List<String> getInterpreters() {
        return interpreters;
    }

    public void setInterpreters(List<String> interpreters) {
        this.interpreters = interpreters == null ? new ArrayList<>() : interpreters;
    }
}
