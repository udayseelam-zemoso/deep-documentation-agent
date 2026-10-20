package com.deepdoc.model;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

public class FileInfo {
    private final Path filePath;
    private final String language;
    private final int linesOfCode;
    private final LocalDateTime lastModified;
    private double importanceScore;

    public FileInfo(Path filePath, String language, int linesOfCode, LocalDateTime lastModified) {
        this.filePath = filePath;
        this.language = language;
        this.linesOfCode = linesOfCode;
        this.lastModified = lastModified;
        this.importanceScore = 0.0;
    }

    public Path getFilePath() {
        return filePath;
    }

    public String getLanguage() {
        return language;
    }

    public int getLinesOfCode() {
        return linesOfCode;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }

    public double getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(double importanceScore) {
        this.importanceScore = importanceScore;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileInfo fileInfo = (FileInfo) o;
        return Objects.equals(filePath, fileInfo.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath);
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "filePath=" + filePath +
                ", language='" + language + '\'' +
                ", linesOfCode=" + linesOfCode +
                ", importanceScore=" + importanceScore +
                '}';
    }
}
