package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class PackageNode {
    private final String name;
    private final List<FileInfo> files = new ArrayList<>();
    private int totalLines;
    private int internalDependencies;

    public PackageNode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public List<FileInfo> getFiles() {
        return Collections.unmodifiableList(files);
    }

    public int getTotalLines() {
        return totalLines;
    }

    /** File-level dependencies whose source and target are both inside this package. */
    public int getInternalDependencies() {
        return internalDependencies;
    }

    void addFile(FileInfo file) {
        files.add(file);
        totalLines += file.getLinesOfCode();
    }

    void incrementInternalDependencies() {
        internalDependencies++;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PackageNode)) return false;
        return name.equals(((PackageNode) o).name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
