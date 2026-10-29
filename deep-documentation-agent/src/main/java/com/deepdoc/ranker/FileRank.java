package com.deepdoc.ranker;

import com.deepdoc.model.FileInfo;

/**
 * Ranking of one file: the raw signals it was computed from, the in-package (local) score and
 * the repository-wide priority, plus the {@link DocLevel} it was assigned. The raw values are
 * kept so the CLI and, later, the documentation generator can explain <em>why</em> a file
 * ranked where it did.
 */
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

    /** File-graph PageRank (all files sum to 1). */
    public double getPageRank() {
        return pageRank;
    }

    /** Recency-weighted commit count; 0 without Git history. */
    public double getActivity() {
        return activity;
    }

    /** Matched an entry-point pattern (main, controller, route, ...). */
    public boolean isEntryPoint() {
        return entryPoint;
    }

    /** Entry point, or used by a file in another package. */
    public boolean isBoundary() {
        return boundary;
    }

    /**
     * Importance within its own package, 0..1: a weighted blend of PageRank and activity, each
     * relative to the best file of the package, and the boundary flag.
     */
    public double getLocalScore() {
        return localScore;
    }

    /**
     * Package score times local score; orders files across the whole repository. Multiplying
     * (rather than adding) means a file needs both an important package and an important
     * place inside it to reach the top.
     */
    public double getPriority() {
        return priority;
    }

    /** LISTED until {@link ImportanceRanker} promotes the file to SUMMARY or DEEP. */
    public DocLevel getLevel() {
        return level;
    }

    void setLevel(DocLevel level) {
        this.level = level;
    }
}
