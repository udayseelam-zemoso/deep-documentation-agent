package com.deepdoc.ranker;

import com.deepdoc.graph.PackageNode;

public final class PackageRank {
    private final PackageNode node;
    private final double pageRank;
    private final double activity;
    private final double structurePercentile;
    private final double activityPercentile;
    private final double score;
    private final int afferent;
    private final int efferent;
    private final PackageRole role;

    PackageRank(PackageNode node, double pageRank, double activity, double structurePercentile,
                double activityPercentile, double score, int afferent, int efferent) {
        this.node = node;
        this.pageRank = pageRank;
        this.activity = activity;
        this.structurePercentile = structurePercentile;
        this.activityPercentile = activityPercentile;
        this.score = score;
        this.afferent = afferent;
        this.efferent = efferent;
        this.role = PackageRole.of(afferent, efferent);
    }

    public PackageNode getNode() {
        return node;
    }

    public String getName() {
        return node.getName();
    }

    public double getPageRank() {
        return pageRank;
    }

    /** Recency-weighted number of commits touching the package. */
    public double getActivity() {
        return activity;
    }

    public double getStructurePercentile() {
        return structurePercentile;
    }

    public double getActivityPercentile() {
        return activityPercentile;
    }

    public double getScore() {
        return score;
    }

    /** Packages that depend on this one (Ca). */
    public int getAfferent() {
        return afferent;
    }

    /** Packages this one depends on (Ce). */
    public int getEfferent() {
        return efferent;
    }

    public PackageRole getRole() {
        return role;
    }
}
