package com.deepdoc.config;

public class RankingSection {
    private double dampingFactor = 0.85;
    private double backEdgeFactor = 0.25;
    private double structureWeight = 0.6;
    private double activityWeight = 0.4;
    private double localStructureWeight = 0.5;
    private double localActivityWeight = 0.3;
    private double localBoundaryWeight = 0.2;
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
