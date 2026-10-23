package com.deepdoc.config;

/**
 * The {@code ranking} section of {@code application.yml}: the weights used by
 * {@code com.deepdoc.ranker.ImportanceRanker}.
 *
 * <pre>
 * package score = structureWeight * pct(PageRank) + activityWeight * pct(ln(1 + activity))
 * local score   = localStructureWeight * PageRank / best in package
 *               + localActivityWeight  * ln(1 + activity) / best in package
 *               + localBoundaryWeight  * (entry point or used by another package ? 1 : 0)
 * priority      = package score * local score
 * </pre>
 */
public class RankingSection {
    /** PageRank probability of following an edge rather than jumping to a random (prior-weighted) node. */
    private double dampingFactor = 0.85;
    /** Weight of the reverse edge added for every dependency, relative to the forward edge. */
    private double backEdgeFactor = 0.25;
    /** Package level: share of the score from graph structure. */
    private double structureWeight = 0.6;
    /** Package level: share of the score from recent Git activity. */
    private double activityWeight = 0.4;
    /** File level: weight of PageRank relative to the best file of the package. */
    private double localStructureWeight = 0.5;
    /** File level: weight of activity relative to the most active file of the package. */
    private double localActivityWeight = 0.3;
    /** File level: bonus for entry points and files used from other packages. */
    private double localBoundaryWeight = 0.2;
    /** Share of all ranked files that get a DEEP (own section) write-up. */
    private double deepFraction = 0.2;

    public double getDampingFactor() {
        return dampingFactor;
    }

    public void setDampingFactor(double dampingFactor) {
        this.dampingFactor = dampingFactor;
    }

    public double getBackEdgeFactor() {
        return backEdgeFactor;
    }

    public void setBackEdgeFactor(double backEdgeFactor) {
        this.backEdgeFactor = backEdgeFactor;
    }

    public double getStructureWeight() {
        return structureWeight;
    }

    public void setStructureWeight(double structureWeight) {
        this.structureWeight = structureWeight;
    }

    public double getActivityWeight() {
        return activityWeight;
    }

    public void setActivityWeight(double activityWeight) {
        this.activityWeight = activityWeight;
    }

    public double getLocalStructureWeight() {
        return localStructureWeight;
    }

    public void setLocalStructureWeight(double localStructureWeight) {
        this.localStructureWeight = localStructureWeight;
    }

    public double getLocalActivityWeight() {
        return localActivityWeight;
    }

    public void setLocalActivityWeight(double localActivityWeight) {
        this.localActivityWeight = localActivityWeight;
    }

    public double getLocalBoundaryWeight() {
        return localBoundaryWeight;
    }

    public void setLocalBoundaryWeight(double localBoundaryWeight) {
        this.localBoundaryWeight = localBoundaryWeight;
    }

    public double getDeepFraction() {
        return deepFraction;
    }

    public void setDeepFraction(double deepFraction) {
        this.deepFraction = deepFraction;
    }
}
