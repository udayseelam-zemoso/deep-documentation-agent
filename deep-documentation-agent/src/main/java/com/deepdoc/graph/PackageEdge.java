package com.deepdoc.graph;

import com.deepdoc.model.SourceReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rolled-up edge {@code P -> Q}: some file in P depends on some file in Q. It keeps up to
 * {@value #MAX_EVIDENCE} source lines as evidence, so generated documentation can cite the
 * import that creates the dependency instead of just asserting it.
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

    /** Sum of the file edges' reference counts: how many times P's code mentions Q. */
    public int getReferenceCount() {
        return referenceCount;
    }

    /** Up to {@value #MAX_EVIDENCE} lines where a file of P refers to a file of Q. */
    public List<SourceReference> getEvidence() {
        return Collections.unmodifiableList(evidence);
    }

    /** Folds one crossing file edge into this package edge. */
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
