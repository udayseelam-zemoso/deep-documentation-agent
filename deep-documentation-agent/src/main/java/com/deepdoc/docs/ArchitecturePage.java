package com.deepdoc.docs;

import com.deepdoc.analyzer.ArchitectureDetector;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.PackageEdge;
import com.deepdoc.graph.PackageNode;
import com.deepdoc.model.ArchitectureStyle;
import com.deepdoc.model.DetectedModule;
import com.deepdoc.model.DetectedPattern;
import com.deepdoc.model.RepositoryContext;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.model.SourceReference;
import com.deepdoc.ranker.PackageRank;
import com.deepdoc.ranker.RankingResult;
import org.jgrapht.Graph;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code architecture.md}: style, modules, framework evidence, and the package graph with
 * Ca/Ce. This is the "how the system is put together" page; per-file detail lives on
 * package pages.
 */
final class ArchitecturePage {
    private ArchitecturePage() {
    }

    static String render(Path fromDoc, RepositoryManifest manifest, DependencyGraph graph,
                         RankingResult ranking, boolean diagrams) {
        RepositoryContext context = manifest.getContext();
        Markdown md = new Markdown();
        md.heading(1, "Architecture");
        md.paragraph(context.getDetectedArchitecture() + " ("
                + ArchitectureDetector.percent(context.getArchitectureConfidence()) + ", "
                + context.getArchitectureStyle().displayName() + ").");
        md.paragraph(styleParagraph(context.getArchitectureStyle()));

        if (!context.getModules().isEmpty()) {
            md.heading(2, "Build modules");
            md.paragraph("A module is a unit the build system already knows about (a Maven POM,"
                    + " an npm package, a Go module, …). It is *deployable* when it looks runnable"
                    + " on its own: a `main`, a Dockerfile, or a start script.");
            List<List<String>> rows = new ArrayList<>();
            for (DetectedModule module : context.getModules()) {
                rows.add(row("`" + module.getRelativePath() + "/`", module.getBuildSystem(),
                        module.isDeployable() ? "yes" : "no"));
            }
            md.table(headers("Path", "Build", "Deployable"), rows);
        }

        List<DetectedPattern> frameworks = new ArrayList<>();
        for (DetectedPattern pattern : context.getPatterns()) {
            if (pattern.isFramework()) {
                frameworks.add(pattern);
            }
        }
        if (!frameworks.isEmpty()) {
            md.heading(2, "Frameworks");
            md.paragraph("Confidence is the fraction of each pattern's signal weight that matched."
                    + " Evidence lines are the source that justified the hit.");
            for (DetectedPattern pattern : frameworks) {
                md.heading(3, pattern.getName() + " ("
                        + ArchitectureDetector.percent(pattern.getConfidence()) + ")");
                int shown = 0;
                for (SourceReference ref : pattern.getEvidence()) {
                    if (shown++ == 5) {
                        break;
                    }
                    md.bullet(SourceCite.cite(fromDoc, manifest.getRepositoryPath(), ref));
                }
                if (shown == 0) {
                    md.paragraph("No source line was recorded for this pattern.");
                } else {
                    md.blank();
                }
            }
        }

        if (!context.getTechnologies().isEmpty()) {
            md.heading(2, "Technologies");
            List<List<String>> tech = new ArrayList<>();
            List<String> keys = new ArrayList<>(context.getTechnologies().keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                tech.add(row(key, context.getTechnologies().get(key)));
            }
            md.table(headers("Category", "Value"), tech);
        }

        md.heading(2, "Package graph");
        md.paragraph("Packages are groups of files chosen for ranking (declared namespace, or"
                + " directory). An arrow means at least one file in the source package imports"
                + " the target. Ca is how many packages depend on this one; Ce is how many it"
                + " depends on.");
        if (diagrams) {
            md.mermaid(MermaidRenderer.packageFlow(graph, ranking));
        }

        Graph<PackageNode, PackageEdge> packages = graph.getPackageGraph();
        List<List<String>> table = new ArrayList<>();
        for (PackageRank pack : ranking.getPackages()) {
            String link = "[" + pack.getName() + "](" + DocNames.packageDocPath(pack.getName()) + ")";
            table.add(row(link, pack.getRole().name(), String.format("%.2f", pack.getScore()),
                    String.format("%.2f", pack.getStructurePercentile()),
                    String.format("%.2f", pack.getActivityPercentile()),
                    pack.getAfferent() + "/" + pack.getEfferent(),
                    String.valueOf(pack.getNode().getFiles().size()),
                    String.valueOf(pack.getNode().getTotalLines())));
        }
        md.table(headers("Package", "Role", "Score", "Structure", "Activity", "Ca/Ce", "Files", "Lines"),
                table);

        md.heading(2, "Package dependencies");
        List<PackageEdge> edges = new ArrayList<>(packages.edgeSet());
        edges.sort(Comparator
                .comparing((PackageEdge e) -> packages.getEdgeSource(e).getName())
                .thenComparing(e -> packages.getEdgeTarget(e).getName()));
        if (edges.isEmpty()) {
            md.paragraph("No cross-package dependencies were found.");
        } else {
            for (PackageEdge edge : edges) {
                PackageNode from = packages.getEdgeSource(edge);
                PackageNode to = packages.getEdgeTarget(edge);
                String line = "`" + from.getName() + "` → `" + to.getName() + "` ("
                        + edge.getImportCount() + (edge.getImportCount() == 1 ? " import" : " imports")
                        + ")";
                md.bullet(line);
                int shown = 0;
                for (SourceReference ref : edge.getEvidence()) {
                    if (shown++ == 3) {
                        break;
                    }
                    md.line("  - " + SourceCite.cite(fromDoc, manifest.getRepositoryPath(), ref));
                }
            }
            md.blank();
        }

        md.paragraph("Grouping depth " + graph.getGroupingDepth()
                + (graph.getStrippedPrefixes().isEmpty() ? "."
                : " after stripping common prefix `" + String.join("`, `", graph.getStrippedPrefixes()) + "`.")
                + " Tests excluded: " + graph.getExcludedTestFiles()
                + ". Non-code files: " + graph.getNonCodeFiles() + ".");
        return md.build();
    }

    static String styleParagraph(ArchitectureStyle style) {
        if (style == ArchitectureStyle.MONOLITH) {
            return "This repository is packaged as a **monolith**: one deployable unit.";
        }
        if (style == ArchitectureStyle.MODULAR_MONOLITH) {
            return "This repository is a **modular monolith**: several build modules, with at"
                    + " most one independently deployable application (or several under one parent"
                    + " build and no ops evidence that they run apart).";
        }
        if (style == ArchitectureStyle.MICROSERVICES) {
            return "This repository looks like **microservices**: two or more independently"
                    + " deployable units, with Docker Compose, multiple Dockerfiles, or a"
                    + " `services/` / `apps/` layout.";
        }
        if (style == ArchitectureStyle.LIBRARY) {
            return "This repository looks like a **library**: modules exist, but none look"
                    + " runnable on their own.";
        }
        return "There was not enough module or Docker evidence to choose a deployment style.";
    }

    private static List<String> headers(String... values) {
        List<String> headers = new ArrayList<>();
        for (String value : values) {
            headers.add(value);
        }
        return headers;
    }

    private static List<String> row(String... values) {
        List<String> row = new ArrayList<>();
        for (String value : values) {
            row.add(value);
        }
        return row;
    }
}
