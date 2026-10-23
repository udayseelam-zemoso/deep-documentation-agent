package com.deepdoc.ranker;

import com.deepdoc.model.FileInfo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Output of {@link ImportanceRanker#rank}: ranked packages and files, both sorted best first. */
public final class RankingResult {
    private final List<PackageRank> packages;
    private final List<FileRank> files;
    private final boolean usedActivity;

    RankingResult(List<PackageRank> packages, List<FileRank> files, boolean usedActivity) {
        this.packages = Collections.unmodifiableList(packages);
        this.files = Collections.unmodifiableList(files);
        this.usedActivity = usedActivity;
    }

    /** Highest score first. */
    public List<PackageRank> getPackages() {
        return packages;
    }

    /** Highest priority first. */
    public List<FileRank> getFiles() {
        return files;
    }

    /** False when there was no usable Git history and only structure was ranked. */
    public boolean usedActivity() {
        return usedActivity;
    }

    /** Files assigned the given level, in priority order. */
    public List<FileRank> filesAt(DocLevel level) {
        return files.stream().filter(f -> f.getLevel() == level).collect(Collectors.toList());
    }

    /** Rank of a file, or null if it was not in the graph. */
    public FileRank rankOf(FileInfo file) {
        return files.stream().filter(f -> f.getFile().equals(file)).findFirst().orElse(null);
    }

    /** Rank of a package by display name, or null if unknown. */
    public PackageRank packageRank(String name) {
        return packages.stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }
}
