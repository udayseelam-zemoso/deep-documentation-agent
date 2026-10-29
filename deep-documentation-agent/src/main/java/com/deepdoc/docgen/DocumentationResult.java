package com.deepdoc.docgen;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Files written by {@link DocumentationService}, plus the section counts the CLI prints. */
public final class DocumentationResult {
    private final Path outputDirectory;
    private final List<Path> writtenFiles;
    private final int packagePages;
    private final int deepSections;
    private final int summarySections;
    private final int listedEntries;
    private final boolean diagrams;

    DocumentationResult(Path outputDirectory, List<Path> writtenFiles, int packagePages,
                        int deepSections, int summarySections, int listedEntries, boolean diagrams) {
        this.outputDirectory = outputDirectory;
        this.writtenFiles = Collections.unmodifiableList(new ArrayList<>(writtenFiles));
        this.packagePages = packagePages;
        this.deepSections = deepSections;
        this.summarySections = summarySections;
        this.listedEntries = listedEntries;
        this.diagrams = diagrams;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public List<Path> getWrittenFiles() {
        return writtenFiles;
    }

    public int getPackagePages() {
        return packagePages;
    }

    public int getDeepSections() {
        return deepSections;
    }

    public int getSummarySections() {
        return summarySections;
    }

    public int getListedEntries() {
        return listedEntries;
    }

    public boolean hasDiagrams() {
        return diagrams;
    }
}
