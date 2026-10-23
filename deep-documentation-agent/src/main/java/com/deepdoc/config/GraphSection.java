package com.deepdoc.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code graph} section of {@code application.yml}: which files enter the dependency graph,
 * how they are grouped into packages ({@code com.deepdoc.graph.PackageGrouper}) and how
 * imports and co-changes become edges.
 */
public class GraphSection {
    /** Tests import everything they test and would look central, so they are excluded by default. */
    private boolean includeTests = false;
    /** The package depth is increased until there are at least this many packages... */
    private int minPackages = 5;
    /** ...but not beyond this many. */
    private int maxPackages = 25;
    /** A package holding more than this share of all files is split one level deeper... */
    private double splitFraction = 0.3;
    /** ...provided it has at least this many files. */
    private int splitMinFiles = 20;
    /** JS/TS import prefix -> directory relative to the repository root, e.g. "@" -> "src". */
    private Map<String, String> jsAliases = new LinkedHashMap<>();
    /** Link files of languages without a dedicated extractor when they are committed together. */
    private boolean coChangeEdges = true;
    private int coChangeMinCommits = 3;
    private double coChangeMinCoupling = 0.5;
    private int coChangeMaxFilesPerCommit = 30;

    public boolean isCoChangeEdges() {
        return coChangeEdges;
    }

    public void setCoChangeEdges(boolean coChangeEdges) {
        this.coChangeEdges = coChangeEdges;
    }

    public int getCoChangeMinCommits() {
        return coChangeMinCommits;
    }

    public void setCoChangeMinCommits(int coChangeMinCommits) {
        this.coChangeMinCommits = coChangeMinCommits;
    }

    public double getCoChangeMinCoupling() {
        return coChangeMinCoupling;
    }

    public void setCoChangeMinCoupling(double coChangeMinCoupling) {
        this.coChangeMinCoupling = coChangeMinCoupling;
    }

    public int getCoChangeMaxFilesPerCommit() {
        return coChangeMaxFilesPerCommit;
    }

    public void setCoChangeMaxFilesPerCommit(int coChangeMaxFilesPerCommit) {
        this.coChangeMaxFilesPerCommit = coChangeMaxFilesPerCommit;
    }

    public Map<String, String> getJsAliases() {
        return jsAliases;
    }

    public void setJsAliases(Map<String, String> jsAliases) {
        this.jsAliases = jsAliases == null ? new LinkedHashMap<>() : jsAliases;
    }

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
