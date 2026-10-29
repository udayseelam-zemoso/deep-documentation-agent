package com.deepdoc.docs;

import com.deepdoc.ranker.PackageRank;
import com.deepdoc.ranker.PackageRole;

/**
 * Opening sentence of a package page. The wording follows Robert C. Martin's instability
 * role: foundations are described by what they provide, orchestrators by what they
 * coordinate. See {@code design/README.md} §5.6.
 */
final class PackageIntro {
    private PackageIntro() {
    }

    static String sentence(PackageRank rank) {
        String name = "`" + rank.getName() + "`";
        PackageRole role = rank.getRole();
        if (role == PackageRole.FOUNDATION) {
            return name + " is a foundation package: other packages depend on it, and it depends"
                    + " on little else. This page describes what it provides.";
        }
        if (role == PackageRole.ORCHESTRATOR) {
            return name + " is an orchestrator: it coordinates other packages and is typically"
                    + " where requests or jobs enter the system.";
        }
        if (role == PackageRole.INTERMEDIATE) {
            return name + " sits between callers and foundations: it both depends on other"
                    + " packages and is depended on.";
        }
        return name + " has no cross-package dependencies in the graph.";
    }

    static String coupling(PackageRank rank) {
        return "Ca/Ce " + rank.getAfferent() + "/" + rank.getEfferent()
                + " (packages in / packages out). Role: " + rank.getRole().name() + ".";
    }
}
