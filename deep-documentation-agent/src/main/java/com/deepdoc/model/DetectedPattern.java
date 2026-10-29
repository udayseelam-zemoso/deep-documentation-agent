package com.deepdoc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One architecture / framework / build / datastore hit, with a confidence in {@code [0, 1]}
 * and the source lines that justified it.
 */
public final class DetectedPattern {
    public static final String KIND_FRAMEWORK = "framework";
    public static final String KIND_ARCHITECTURE = "architecture";
    public static final String KIND_BUILD = "build";
    public static final String KIND_DATASTORE = "datastore";

    private final String name;
    private final String kind;
    private final String layer;
    private final String architectureLabel;
    private final double confidence;
    private final List<SourceReference> evidence;

    public DetectedPattern(String name, String kind, String layer, String architectureLabel,
                           double confidence, List<SourceReference> evidence) {
        this.name = name;
        this.kind = kind;
        this.layer = layer;
        this.architectureLabel = architectureLabel;
        this.confidence = confidence;
        this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    /** backend, frontend, mobile, or empty. */
    public String getLayer() {
        return layer;
    }

    /** Phrase to use when this pattern is the repository's architecture, or null. */
    public String getArchitectureLabel() {
        return architectureLabel;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<SourceReference> getEvidence() {
        return evidence;
    }

    public boolean isFramework() {
        return KIND_FRAMEWORK.equals(kind) || KIND_ARCHITECTURE.equals(kind);
    }

    @Override
    public String toString() {
        return name + " (" + String.format("%.0f%%", confidence * 100) + ")";
    }
}
