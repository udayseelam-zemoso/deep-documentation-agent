package com.deepdoc.graph;

import com.deepdoc.model.SourceReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rolled-up edge {@code P -> Q}: some file in P depends on some file in Q.
 */
public class PackageEdge {
    static final int MAX_EVIDENCE = 3;

    private int importCount;
    private int referenceCount;
    private final List<SourceReference> evidence = new ArrayList<>();

    public PackageEdge() {
    }

    /** Number of file-level dependencies crossing from P to Q. */
    public int getImportCount() {
        return importCount;
    }

    public int getReferenceCount() {
        return referenceCount;
    }

    public List<SourceReference> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    void add(DependencyEdge fileEdge) {
        importCount++;
        referenceCount += fileEdge.getReferenceCount();
        if (fileEdge.getEvidence() != null && evidence.size() < MAX_EVIDENCE) {
            evidence.add(fileEdge.getEvidence());
        }
    }

    @Override
    public String toString() {
        return "imports=" + importCount;
    }
}
