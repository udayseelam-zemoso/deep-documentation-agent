package com.deepdoc.ranker;

import com.deepdoc.graph.PackageNode;

/**
 * Ranking of one package: raw PageRank and activity, their percentiles among all packages, the
 * blended score, and the coupling numbers (Ca/Ce) with the resulting {@link PackageRole}.
 */
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

    /** Package-graph PageRank (all packages sum to 1). */
    public double getPageRank() {
        return pageRank;
    }

    /** Recency-weighted number of commits touching the package. */
    public double getActivity() {
        return activity;
    }

    /** Position of the package's PageRank among all packages: 0 = lowest, 1 = highest. */
    public double getStructurePercentile() {
        return structurePercentile;
    }

    /** Position of ln(1 + activity) among all packages; 0 when history is not used. */
    public double getActivityPercentile() {
        return activityPercentile;
    }

    /** Blend of the two percentiles (weights from the {@code ranking} config), 0..1. */
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

    /** Architectural role derived from Ca and Ce; used to phrase the package's documentation. */
    public PackageRole getRole() {
        return role;
    }
}
