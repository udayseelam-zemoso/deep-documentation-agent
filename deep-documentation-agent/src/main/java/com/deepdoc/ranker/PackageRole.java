package com.deepdoc.ranker;

/**
 * Architectural role from Robert C. Martin's instability {@code I = Ce / (Ca + Ce)},
 * where Ca counts packages depending on this one and Ce the packages it depends on.
 * The role tells the documentation generator how to introduce a package: foundations are
 * described by what they provide, orchestrators by what they coordinate.
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

    /** @param afferent Ca, incoming package edges; @param efferent Ce, outgoing package edges */
    static PackageRole of(int afferent, int efferent) {
        if (afferent + efferent == 0) return ISOLATED;
        double instability = (double) efferent / (afferent + efferent);
        if (instability <= 0.3) return FOUNDATION;
        if (instability >= 0.7) return ORCHESTRATOR;
        return INTERMEDIATE;
    }
}
