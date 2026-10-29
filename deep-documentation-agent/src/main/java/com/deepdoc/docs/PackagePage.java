package com.deepdoc.docs;

import com.deepdoc.graph.DependencyEdge;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.PackageEdge;
import com.deepdoc.graph.PackageNode;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.SourceReference;
import com.deepdoc.ranker.DocLevel;
import com.deepdoc.ranker.FileRank;
import com.deepdoc.ranker.PackageRank;
import com.deepdoc.ranker.RankingResult;
import com.deepdoc.understand.DeclaredSymbol;
import com.deepdoc.understand.FileOutline;
import com.deepdoc.understand.KnowledgeGraph;
import com.deepdoc.understand.UnderstandingResult;
import org.jgrapht.Graph;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One Markdown page per ranked package. DEEP files get a full section (purpose, types,
 * methods, uses, citations). SUMMARY files get a paragraph. LISTED files are a table of
 * names, matching the Week 1 documentation levels.
 */
final class PackagePage {
    static final int MAX_METHODS = 16;

    final String markdown;
    final int deepSections;
    final int summarySections;
    final int listedEntries;

    private PackagePage(String markdown, int deepSections, int summarySections, int listedEntries) {
        this.markdown = markdown;
        this.deepSections = deepSections;
        this.summarySections = summarySections;
        this.listedEntries = listedEntries;
    }

    static PackagePage render(Path fromDoc, Path repositoryRoot, PackageRank pack,
                              DependencyGraph graph, RankingResult ranking,
                              UnderstandingResult understanding, boolean diagrams) {
        List<FileRank> members = members(pack, ranking);
        int deep = 0;
        int summary = 0;
        int listed = 0;
        for (FileRank member : members) {
            if (member.getLevel() == DocLevel.DEEP) {
                deep++;
            } else if (member.getLevel() == DocLevel.SUMMARY) {
                summary++;
            } else {
                listed++;
            }
        }

        Markdown md = new Markdown();
        md.heading(1, "Package `" + pack.getName() + "`");
        md.paragraph(PackageIntro.sentence(pack));
        md.paragraph(pack.getNode().getFiles().size() + " files, "
                + pack.getNode().getTotalLines() + " lines. Score "
                + String.format("%.2f", pack.getScore()) + ". " + PackageIntro.coupling(pack)
                + " Internal file dependencies: " + pack.getNode().getInternalDependencies() + ".");

        if (diagrams) {
            md.mermaid(neighbourFlow(pack, graph));
        }

        writeNeighbours(md, fromDoc, repositoryRoot, pack, graph);

        KnowledgeGraph knowledge = understanding == null ? null : understanding.getKnowledge();

        List<FileRank> deepFiles = new ArrayList<>();
        List<FileRank> summaryFiles = new ArrayList<>();
        List<FileRank> listedFiles = new ArrayList<>();
        for (FileRank member : members) {
            if (member.getLevel() == DocLevel.DEEP) {
                deepFiles.add(member);
            } else if (member.getLevel() == DocLevel.SUMMARY) {
                summaryFiles.add(member);
            } else {
                listedFiles.add(member);
            }
        }

        if (!deepFiles.isEmpty()) {
            md.heading(2, "Key files");
            for (FileRank rank : deepFiles) {
                writeDeep(md, fromDoc, repositoryRoot, graph, rank, knowledge);
            }
        }

        if (!summaryFiles.isEmpty()) {
            md.heading(2, "Also notable");
            for (FileRank rank : summaryFiles) {
                writeSummary(md, fromDoc, repositoryRoot, graph, rank, knowledge);
            }
        }

        if (!listedFiles.isEmpty()) {
            md.heading(2, "Other files");
            List<List<String>> rows = new ArrayList<>();
            for (FileRank rank : listedFiles) {
                FileOutline outline = knowledge == null ? null : knowledge.outlineOf(rank.getFile());
                String role = outline != null && !outline.getResponsibility().isEmpty()
                        ? outline.getResponsibility() : DocNames.listedRole(rank.getFile());
                String path = graph.relativePath(rank.getFile());
                rows.add(row(SourceCite.fileLink(fromDoc, rank.getFile().getFilePath(), path),
                        rank.getFile().getLanguage(),
                        String.valueOf(rank.getFile().getLinesOfCode()),
                        role));
            }
            md.table(headers("File", "Language", "Lines", "Role"), rows);
        }

        md.paragraph("[All packages](../README.md) · [Architecture](../architecture.md)");
        return new PackagePage(md.build(), deep, summary, listed);
    }

    /** This package in the middle, with arrows to the packages it depends on. */
    private static String neighbourFlow(PackageRank pack, DependencyGraph graph) {
        Graph<PackageNode, PackageEdge> packages = graph.getPackageGraph();
        PackageNode node = pack.getNode();
        StringBuilder sb = new StringBuilder();
        sb.append("flowchart LR\n");
        sb.append("    ").append(MermaidRenderer.nodeId(node.getName()))
                .append("[\"").append(MermaidRenderer.escapeLabel(node.getName())).append("\"]\n");
        List<PackageEdge> outgoing = new ArrayList<>(packages.outgoingEdgesOf(node));
        outgoing.sort(Comparator.comparing(e -> packages.getEdgeTarget(e).getName()));
        for (PackageEdge edge : outgoing) {
            PackageNode target = packages.getEdgeTarget(edge);
            sb.append("    ").append(MermaidRenderer.nodeId(node.getName()))
                    .append(" --> ").append(MermaidRenderer.nodeId(target.getName()))
                    .append("[\"").append(MermaidRenderer.escapeLabel(target.getName())).append("\"]\n");
        }
        return outgoing.isEmpty() ? "" : sb.toString();
    }

    private static void writeNeighbours(Markdown md, Path fromDoc, Path repositoryRoot,
                                        PackageRank pack, DependencyGraph graph) {
        Graph<PackageNode, PackageEdge> packages = graph.getPackageGraph();
        PackageNode node = pack.getNode();
        List<PackageEdge> outgoing = new ArrayList<>(packages.outgoingEdgesOf(node));
        outgoing.sort(Comparator.comparingInt(PackageEdge::getImportCount).reversed()
                .thenComparing(e -> packages.getEdgeTarget(e).getName()));
        List<PackageEdge> incoming = new ArrayList<>(packages.incomingEdgesOf(node));
        incoming.sort(Comparator.comparingInt(PackageEdge::getImportCount).reversed()
                .thenComparing(e -> packages.getEdgeSource(e).getName()));

        if (!outgoing.isEmpty()) {
            md.heading(2, "Depends on");
            for (PackageEdge edge : outgoing) {
                PackageNode target = packages.getEdgeTarget(edge);
                md.bullet("[" + target.getName() + "](" + DocNames.packageFileName(target.getName()) + ") — "
                        + edge.getImportCount() + (edge.getImportCount() == 1 ? " import" : " imports"));
                int shown = 0;
                for (SourceReference ref : edge.getEvidence()) {
                    if (shown++ == 3) {
                        break;
                    }
                    md.line("  - " + SourceCite.cite(fromDoc, repositoryRoot, ref));
                }
            }
            md.blank();
        }

        if (!incoming.isEmpty()) {
            md.heading(2, "Used by");
            for (PackageEdge edge : incoming) {
                PackageNode source = packages.getEdgeSource(edge);
                md.bullet("[" + source.getName() + "](" + DocNames.packageFileName(source.getName()) + ") — "
                        + edge.getImportCount() + (edge.getImportCount() == 1 ? " import" : " imports"));
            }
            md.blank();
        }
    }

    private static void writeDeep(Markdown md, Path fromDoc, Path repositoryRoot,
                                  DependencyGraph graph, FileRank rank, KnowledgeGraph knowledge) {
        FileInfo file = rank.getFile();
        FileOutline outline = knowledge == null ? null : knowledge.outlineOf(file);
        String path = graph.relativePath(file);
        md.heading(3, "`" + file.getFilePath().getFileName() + "` (DEEP)");
        if (outline != null && !outline.getResponsibility().isEmpty()) {
            md.paragraph(outline.getResponsibility());
        } else {
            md.paragraph(DocNames.listedRole(file));
        }

        String sourceLink = SourceCite.fileLink(fromDoc, file.getFilePath(), path);
        String flags = flags(rank);
        md.bullet("**Source:** " + sourceLink + " · " + file.getLanguage() + " · "
                + file.getLinesOfCode() + " lines");
        if (outline != null) {
            md.bullet("**Parsed with:** " + engineLabel(outline)
                    + (outline.getResponsibilitySource().isEmpty() ? ""
                    : " · responsibility from " + outline.getResponsibilitySource()));
            if (!outline.getNamespace().isEmpty()) {
                md.bullet("**Namespace:** `" + Markdown.code(outline.getNamespace()) + "`");
            }
        }
        if (!flags.isEmpty()) {
            md.bullet("**Flags:** " + flags);
        }
        md.blank();

        if (outline != null && !outline.types().isEmpty()) {
            md.heading(4, "Types");
            for (DeclaredSymbol type : outline.types()) {
                String cite = type.getLine() > 0
                        ? SourceCite.lineLink(fromDoc, file.getFilePath(), type.getLine(),
                        file.getFilePath().getFileName() + ":" + type.getLine())
                        : path;
                StringBuilder line = new StringBuilder();
                line.append("`").append(Markdown.code(type.getName())).append("` ")
                        .append(type.getKind().name().toLowerCase()).append(" at ").append(cite);
                if (!type.getJavadoc().isEmpty()) {
                    line.append(" — ").append(type.getJavadoc());
                }
                md.bullet(line.toString());
                if (!type.getAnnotations().isEmpty()) {
                    md.line("  - annotations: `" + Markdown.code(String.join("`, `", type.getAnnotations())) + "`");
                }
                if (!type.getParents().isEmpty()) {
                    md.line("  - extends / implements: `" + Markdown.code(String.join("`, `", type.getParents())) + "`");
                }
            }
            md.blank();
        }

        if (outline != null) {
            List<String> methods = outline.methodNames();
            if (!methods.isEmpty()) {
                md.heading(4, "Methods");
                List<String> shown = methods.size() > MAX_METHODS ? methods.subList(0, MAX_METHODS) : methods;
                md.paragraph("`" + Markdown.code(String.join("`, `", shown)) + "`"
                        + (methods.size() > MAX_METHODS ? " (" + methods.size() + " total)" : ""));
            }
        }

        writeUses(md, fromDoc, repositoryRoot, graph, file, knowledge);
        writeDependents(md, fromDoc, graph, file);
    }

    private static void writeSummary(Markdown md, Path fromDoc, Path repositoryRoot,
                                     DependencyGraph graph, FileRank rank, KnowledgeGraph knowledge) {
        FileInfo file = rank.getFile();
        FileOutline outline = knowledge == null ? null : knowledge.outlineOf(file);
        String path = graph.relativePath(file);
        md.heading(3, "`" + file.getFilePath().getFileName() + "` (SUMMARY)");
        if (outline != null && !outline.getResponsibility().isEmpty()) {
            md.paragraph(outline.getResponsibility());
        } else {
            md.paragraph(DocNames.listedRole(file));
        }
        StringBuilder meta = new StringBuilder();
        meta.append(SourceCite.fileLink(fromDoc, file.getFilePath(), path))
                .append(" · ").append(file.getLanguage())
                .append(" · ").append(file.getLinesOfCode()).append(" lines");
        if (outline != null && !outline.typeNames().isEmpty()) {
            meta.append(" · types: `").append(Markdown.code(String.join("`, `", outline.typeNames()))).append("`");
        }
        String flags = flags(rank);
        if (!flags.isEmpty()) {
            meta.append(" · ").append(flags);
        }
        md.paragraph(meta.toString());

        if (knowledge != null && !knowledge.uses(file).isEmpty()) {
            List<String> used = new ArrayList<>();
            for (FileInfo target : knowledge.uses(file)) {
                used.add(SourceCite.fileLink(fromDoc, target.getFilePath(), graph.relativePath(target)));
            }
            md.paragraph("Uses " + String.join(", ", used) + ".");
        }
    }

    private static void writeUses(Markdown md, Path fromDoc, Path repositoryRoot,
                                  DependencyGraph graph, FileInfo file, KnowledgeGraph knowledge) {
        List<FileInfo> used = new ArrayList<>();
        if (knowledge != null) {
            used.addAll(knowledge.uses(file));
        }
        if (used.isEmpty()) {
            Graph<FileInfo, DependencyEdge> files = graph.getFileGraph();
            if (files.containsVertex(file)) {
                for (DependencyEdge edge : files.outgoingEdgesOf(file)) {
                    FileInfo target = files.getEdgeTarget(edge);
                    if (!used.contains(target)) {
                        used.add(target);
                    }
                }
            }
        }
        if (used.isEmpty()) {
            return;
        }
        used.sort(Comparator.comparing(graph::relativePath));
        md.heading(4, "Uses");
        Graph<FileInfo, DependencyEdge> files = graph.getFileGraph();
        for (FileInfo target : used) {
            String link = SourceCite.fileLink(fromDoc, target.getFilePath(), graph.relativePath(target));
            md.bullet(link);
            if (files.containsVertex(file) && files.containsVertex(target)) {
                DependencyEdge edge = files.getEdge(file, target);
                if (edge != null && edge.getEvidence() != null) {
                    md.line("  - " + SourceCite.cite(fromDoc, repositoryRoot, edge.getEvidence()));
                } else if (edge != null && edge.isCoChange()) {
                    md.line("  - inferred from files being committed together");
                }
            }
        }
        md.blank();
    }

    private static void writeDependents(Markdown md, Path fromDoc, DependencyGraph graph, FileInfo file) {
        Graph<FileInfo, DependencyEdge> files = graph.getFileGraph();
        if (!files.containsVertex(file)) {
            return;
        }
        List<FileInfo> callers = new ArrayList<>();
        for (DependencyEdge edge : files.incomingEdgesOf(file)) {
            callers.add(files.getEdgeSource(edge));
        }
        if (callers.isEmpty()) {
            return;
        }
        callers.sort(Comparator.comparing(graph::relativePath));
        md.heading(4, "Used by");
        for (FileInfo caller : callers) {
            md.bullet(SourceCite.fileLink(fromDoc, caller.getFilePath(), graph.relativePath(caller)));
        }
        md.blank();
    }

    private static List<FileRank> members(PackageRank pack, RankingResult ranking) {
        List<FileRank> members = new ArrayList<>();
        for (FileRank rank : ranking.getFiles()) {
            if (rank.getPackageRank().getName().equals(pack.getName())) {
                members.add(rank);
            }
        }
        return members;
    }

    private static String flags(FileRank rank) {
        List<String> flags = new ArrayList<>();
        if (rank.isEntryPoint()) {
            flags.add("entry point");
        }
        if (rank.isBoundary()) {
            flags.add("boundary");
        }
        return String.join(", ", flags);
    }

    private static String engineLabel(FileOutline outline) {
        if (FileOutline.ENGINE_JAVAPARSER.equals(outline.getEngine())) {
            return "JavaParser (AST)";
        }
        if (FileOutline.ENGINE_LEXICAL.equals(outline.getEngine())) {
            return "lexical scan";
        }
        return outline.getEngine();
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
