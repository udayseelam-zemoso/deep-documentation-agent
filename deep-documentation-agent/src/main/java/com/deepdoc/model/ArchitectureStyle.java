package com.deepdoc.model;

/**
 * How the repository is packaged and deployed, derived from modules and Docker/compose
 * evidence rather than from a framework name. A Spring Boot repo can be any of these.
 */
public enum ArchitectureStyle {
    /** One deployable unit, no extra modules. */
    MONOLITH,
    /** Several build modules, at most one independently deployable (one app + libraries). */
    MODULAR_MONOLITH,
    /** Two or more independently deployable units (own main, Dockerfile, or compose service). */
    MICROSERVICES,
    /** Source is published or depended on, but nothing looks runnable on its own. */
    LIBRARY,
    /** Not enough evidence to choose. */
    UNKNOWN;

    public String displayName() {
        switch (this) {
            case MONOLITH:
                return "monolith";
            case MODULAR_MONOLITH:
                return "modular monolith";
            case MICROSERVICES:
                return "microservices";
            case LIBRARY:
                return "library";
            default:
                return "unknown";
        }
    }
}
