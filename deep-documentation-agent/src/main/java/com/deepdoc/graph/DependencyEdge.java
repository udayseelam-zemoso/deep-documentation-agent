package com.deepdoc.graph;

import com.deepdoc.model.SourceReference;

/**
 * File-level edge {@code A -> B}: file A depends on file B.
 * Needs a public no-arg constructor because JGraphT creates edges reflectively.
 */
public class DependencyEdge {
    private int referenceCount;
    private SourceReference evidence;
    private boolean coChange;

    public DependencyEdge() {
    }

    /**
     * True when the edge was inferred from files being committed together ({@link CoChangeEdges})
     * rather than from a reference in the code; such edges carry no evidence line.
     */
    public boolean isCoChange() {
        return coChange;
    }

    void markCoChange() {
        this.coChange = true;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    void addReferences(int count) {
        this.referenceCount += count;
    }

    public SourceReference getEvidence() {
        return evidence;
    }

    void setEvidenceIfAbsent(SourceReference evidence) {
        if (this.evidence == null) {
            this.evidence = evidence;
        }
    }

    @Override
    public String toString() {
        return "refs=" + referenceCount;
    }
}
