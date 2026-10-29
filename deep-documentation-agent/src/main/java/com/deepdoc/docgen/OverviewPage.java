package com.deepdoc.docgen;

import com.deepdoc.analyzer.ArchitectureDetector;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.model.DetectedModule;
import com.deepdoc.model.RepositoryContext;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.ranker.DocLevel;
import com.deepdoc.ranker.FileRank;
import com.deepdoc.ranker.PackageRank;
import com.deepdoc.ranker.RankingResult;
import com.deepdoc.understand.FileOutline;
import com.deepdoc.understand.UnderstandingResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code README.md} at the root of the output folder: what the system is, a package diagram,
 * and links into the rest of the generated docs.
 */
final class OverviewPage {
    private OverviewPage() {
    }

    static String render(Path fromDoc, RepositoryManifest manifest, DependencyGraph graph,
                         RankingResult ranking, UnderstandingResult understanding,
                         boolean diagrams) {
        RepositoryContext context = manifest.getContext();
        Markdown md = new Markdown();
        md.heading(1, title(manifest));
        md.paragraph(opening(manifest, understanding));

        md.heading(2, "At a glance");
        List<List<String>> glance = new ArrayList<>();
        glance.add(row("Architecture", context.getDetectedArchitecture() + " ("
                + ArchitectureDetector.percent(context.getArchitectureConfidence()) + ", "
                + context.getArchitectureStyle().displayName() + ")"));
        glance.add(row("Files", manifest.getTotalFiles() + " (" + manifest.getTotalLines() + " lines)"));
        glance.add(row("Languages", languageSummary(manifest)));
        glance.add(row("Documentation", ranking.filesAt(DocLevel.DEEP).size() + " deep, "
                + ranking.filesAt(DocLevel.SUMMARY).size() + " summary, "
                + ranking.filesAt(DocLevel.LISTED).size() + " listed"));
        glance.add(row("Ranking", ranking.usedActivity()
                ? "structure + Git activity" : "structure only (no usable Git history)"));
        if (understanding != null) {
            glance.add(row("Outlines", understanding.getOutlines().size() + " files ("
                    + understanding.getAstFiles() + " JavaParser, "
                    + understanding.getLexicalFiles() + " lexical); LLM: "
                    + understanding.getLlmMode()));
        }
        md.table(headers("Item", "Value"), glance);

        if (!context.getModules().isEmpty()) {
            md.heading(2, "Modules");
            for (DetectedModule module : context.getModules()) {
                md.bullet("`" + module.getRelativePath() + "/` — " + module.getBuildSystem()
                        + (module.isDeployable() ? ", deployable" : ""));
            }
            md.blank();
        }

        md.heading(2, "Architecture");
        md.paragraph("See [architecture.md](architecture.md) for modules, evidence and coupling."
                + " The diagram below is the package graph used for ranking: an arrow"
                + " `A --> B` means some file in A depends on some file in B.");
        if (diagrams) {
            md.mermaid(MermaidRenderer.packageFlow(graph, ranking));
        }

        md.heading(2, "Packages");
        List<List<String>> packages = new ArrayList<>();
        for (PackageRank pack : ranking.getPackages()) {
            String link = "[" + pack.getName() + "](" + DocNames.packageDocPath(pack.getName()) + ")";
            packages.add(row(link, pack.getRole().name(), String.format("%.2f", pack.getScore()),
                    pack.getAfferent() + "/" + pack.getEfferent(),
                    String.valueOf(pack.getNode().getFiles().size())));
        }
        md.table(headers("Package", "Role", "Score", "Ca/Ce", "Files"), packages);

        md.heading(2, "How to read these docs");
        md.bullet("**DEEP** — its own section on the package page: purpose, types, methods,"
                + " and the files it uses, each with a source link.");
        md.bullet("**SUMMARY** — a short paragraph on the package page (best file of a package"
                + " with no DEEP file, and every entry point).");
        md.bullet("**LISTED** — name and one-line role in the package file list.");
        md.bullet("Claims that come from the code cite `file:line`. Hover or click to open the source.");
        md.blank();

        List<FileRank> deep = ranking.filesAt(DocLevel.DEEP);
        if (!deep.isEmpty()) {
            md.heading(2, "Start here");
            int shown = Math.min(5, deep.size());
            for (int i = 0; i < shown; i++) {
                FileRank rank = deep.get(i);
                String path = graph.relativePath(rank.getFile());
                String page = DocNames.packageDocPath(rank.getPackageRank().getName());
                String responsibility = "";
                if (understanding != null) {
                    FileOutline outline = understanding.getKnowledge().outlineOf(rank.getFile());
                    if (outline != null && !outline.getResponsibility().isEmpty()) {
                        responsibility = " — " + outline.getResponsibility();
                    }
                }
                md.bullet("[" + path + "](" + page + ")" + responsibility);
            }
            md.blank();
        }

        md.paragraph("_Generated by Deep Documentation Agent from the source in this repository."
                + " Re-run `analyze` to refresh._");
        return md.build();
    }

    private static String title(RepositoryManifest manifest) {
        Path root = manifest.getRepositoryPath();
        if (root == null || root.getFileName() == null) {
            return "Repository documentation";
        }
        String name = root.getFileName().toString();
        if (name.isEmpty() || ".".equals(name)) {
            return "Repository documentation";
        }
        return name;
    }

    private static String opening(RepositoryManifest manifest, UnderstandingResult understanding) {
        RepositoryContext context = manifest.getContext();
        StringBuilder sb = new StringBuilder();
        String architecture = context.getDetectedArchitecture();
        if (architecture != null && !architecture.isEmpty() && !"Unknown".equals(architecture)) {
            sb.append("This repository is a ").append(architecture)
                    .append(" (").append(ArchitectureDetector.percent(context.getArchitectureConfidence()))
                    .append(", ").append(context.getArchitectureStyle().displayName()).append("). ");
        } else {
            sb.append("This repository has no single detected framework. ");
        }
        sb.append("It contains ").append(manifest.getTotalFiles()).append(" files (")
                .append(manifest.getTotalLines()).append(" lines)");
        String languages = languageSummary(manifest);
        if (!languages.isEmpty() && !"—".equals(languages)) {
            sb.append(" across ").append(languages);
        }
        sb.append('.');
        if (understanding != null) {
            List<String> sentences = new ArrayList<>();
            for (FileOutline outline : understanding.deepOutlines()) {
                if (!outline.getResponsibility().isEmpty()) {
                    sentences.add(outline.getResponsibility());
                }
                if (sentences.size() == 3) {
                    break;
                }
            }
            if (!sentences.isEmpty()) {
                sb.append(" The files that ranked highest ");
                if (sentences.size() == 1) {
                    sb.append(uncapitalize(sentences.get(0)));
                } else {
                    sb.append("are responsible for: ").append(joinAnd(sentences)).append('.');
                }
            }
        }
        return sb.toString();
    }

    private static String languageSummary(RepositoryManifest manifest) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(manifest.getLanguageStats().entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey()));
        if (entries.isEmpty()) {
            return "—";
        }
        List<String> parts = new ArrayList<>();
        int shown = Math.min(5, entries.size());
        for (int i = 0; i < shown; i++) {
            Map.Entry<String, Integer> entry = entries.get(i);
            parts.add(entry.getKey() + " " + entry.getValue() + " lines");
        }
        if (entries.size() > shown) {
            parts.add((entries.size() - shown) + " more");
        }
        return String.join(", ", parts);
    }

    private static String uncapitalize(String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return sentence;
        }
        return Character.toLowerCase(sentence.charAt(0)) + sentence.substring(1);
    }

    private static String joinAnd(List<String> parts) {
        if (parts.size() == 1) {
            return trimDot(parts.get(0));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(i == parts.size() - 1 ? "; and " : "; ");
            }
            sb.append(trimDot(parts.get(i)));
        }
        return sb.toString();
    }

    private static String trimDot(String text) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.endsWith(".")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
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
