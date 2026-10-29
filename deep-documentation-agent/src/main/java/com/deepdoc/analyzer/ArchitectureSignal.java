package com.deepdoc.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * One check in {@code architectures.yml}. Setter-based so SnakeYAML can bind it.
 *
 * @see ArchitectureDetector
 */
public class ArchitectureSignal {
    private String type = "file-contains";
    private List<String> paths = new ArrayList<>();
    private List<String> files = new ArrayList<>();
    private String text = "";
    private boolean regex;
    private double weight = 1.0;
    private int min = 1;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type == null ? "file-contains" : type;
    }

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths == null ? new ArrayList<>() : paths;
    }

    public List<String> getFiles() {
        return files;
    }

    public void setFiles(List<String> files) {
        this.files = files == null ? new ArrayList<>() : files;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public int getMin() {
        return min;
    }

    public void setMin(int min) {
        this.min = min;
    }
}
