package com.deepdoc.ranker;

import com.deepdoc.model.FileInfo;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    public boolean usedActivity() {
        return usedActivity;
    }

    public List<FileRank> filesAt(DocLevel level) {
        return files.stream().filter(f -> f.getLevel() == level).collect(Collectors.toList());
    }

    public FileRank rankOf(FileInfo file) {
        return files.stream().filter(f -> f.getFile().equals(file)).findFirst().orElse(null);
    }

    public PackageRank packageRank(String name) {
        return packages.stream().filter(p -> p.getName().equals(name)).findFirst().orElse(null);
    }
}
