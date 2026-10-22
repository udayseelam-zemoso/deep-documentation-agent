package com.deepdoc.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepositoryManifest {
    private final Path repositoryPath;
    private final List<FileInfo> files;
    private final Map<String, Integer> languageStats;
    private final RepositoryContext context;
    private int totalLines;

    public RepositoryManifest(Path repositoryPath) {
        this.repositoryPath = repositoryPath;
        this.files = new ArrayList<>();
        this.languageStats = new HashMap<>();
        this.context = new RepositoryContext();
        this.totalLines = 0;
    }

    public void addFile(FileInfo fileInfo) {
        files.add(fileInfo);
        totalLines += fileInfo.getLinesOfCode();
        languageStats.merge(fileInfo.getLanguage(), fileInfo.getLinesOfCode(), Integer::sum);
    }

    public Path getRepositoryPath() {
        return repositoryPath;
    }

    public List<FileInfo> getFiles() {
        return files;
    }

    public Map<String, Integer> getLanguageStats() {
        return languageStats;
    }

    public RepositoryContext getContext() {
        return context;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public int getTotalFiles() {
        return files.size();
    }

    @Override
    public String toString() {
        return "RepositoryManifest{" +
                "repositoryPath=" + repositoryPath +
                ", totalFiles=" + files.size() +
                ", totalLines=" + totalLines +
                ", languages=" + languageStats.keySet() +
                '}';
    }
}
