package com.deepdoc.config;

public class GraphSection {
    private boolean includeTests = false;
    private int minPackages = 5;
    private int maxPackages = 25;
    private double splitFraction = 0.3;
    private int splitMinFiles = 20;

    public boolean isIncludeTests() {
        return includeTests;
    }

    public void setIncludeTests(boolean includeTests) {
        this.includeTests = includeTests;
    }

    public int getMinPackages() {
        return minPackages;
    }

    public void setMinPackages(int minPackages) {
        this.minPackages = minPackages;
    }

    public int getMaxPackages() {
        return maxPackages;
    }

    public void setMaxPackages(int maxPackages) {
        this.maxPackages = maxPackages;
    }

    public double getSplitFraction() {
        return splitFraction;
    }

    public void setSplitFraction(double splitFraction) {
        this.splitFraction = splitFraction;
    }

    public int getSplitMinFiles() {
        return splitMinFiles;
    }

    public void setSplitMinFiles(int splitMinFiles) {
        this.splitMinFiles = splitMinFiles;
    }
}
