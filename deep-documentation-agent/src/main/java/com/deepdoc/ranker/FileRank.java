package com.deepdoc.ranker;

import com.deepdoc.model.FileInfo;

public final class FileRank {
    private final FileInfo file;
    private final PackageRank packageRank;
    private final double pageRank;
    private final double activity;
    private final boolean entryPoint;
    private final boolean boundary;
    private final double localScore;
    private final double priority;
    private DocLevel level = DocLevel.LISTED;

    FileRank(FileInfo file, PackageRank packageRank, double pageRank, double activity,
             boolean entryPoint, boolean boundary, double localScore) {
        this.file = file;
        this.packageRank = packageRank;
        this.pageRank = pageRank;
        this.activity = activity;
        this.entryPoint = entryPoint;
        this.boundary = boundary;
        this.localScore = localScore;
        this.priority = packageRank.getScore() * localScore;
    }

    public FileInfo getFile() {
        return file;
    }

    public PackageRank getPackageRank() {
        return packageRank;
    }

    public double getPageRank() {
        return pageRank;
    }

    public double getActivity() {
        return activity;
    }

    public boolean isEntryPoint() {
        return entryPoint;
    }

    /** Entry point, or used by a file in another package. */
    public boolean isBoundary() {
        return boundary;
    }

    /** Importance within its own package, 0..1. */
    public double getLocalScore() {
        return localScore;
    }

    /** Package score times local score; orders files across the whole repository. */
    public double getPriority() {
        return priority;
    }

    public DocLevel getLevel() {
        return level;
    }

    void setLevel(DocLevel level) {
        this.level = level;
    }
}
