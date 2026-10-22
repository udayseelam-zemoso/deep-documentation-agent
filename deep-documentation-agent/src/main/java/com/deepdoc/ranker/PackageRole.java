package com.deepdoc.ranker;

/**
 * Architectural role from Robert C. Martin's instability {@code I = Ce / (Ca + Ce)},
 * where Ca counts packages depending on this one and Ce the packages it depends on.
 */
public enum PackageRole {
    /** I <= 0.3: many dependents, few dependencies (domain, util). */
    FOUNDATION,
    /** 0.3 < I < 0.7. */
    INTERMEDIATE,
    /** I >= 0.7: depends on many, few depend on it (controllers, wiring). */
    ORCHESTRATOR,
    /** No cross-package dependencies at all. */
    ISOLATED;

    static PackageRole of(int afferent, int efferent) {
        if (afferent + efferent == 0) return ISOLATED;
        double instability = (double) efferent / (afferent + efferent);
        if (instability <= 0.3) return FOUNDATION;
        if (instability >= 0.7) return ORCHESTRATOR;
        return INTERMEDIATE;
    }
}
