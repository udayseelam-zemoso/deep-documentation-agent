package com.deepdoc.understand;

import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.FileRank;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One sentence for "what is this file for?", used as the seed for Week 3 documentation.
 *
 * <ol>
 *   <li>The first Javadoc / doc-comment sentence, if the author already wrote one.</li>
 *   <li>Otherwise a heuristic from the type name ({@code OrderController} handles HTTP),
 *       annotations ({@code @RestController}), and whether the file is an entry point.</li>
 *   <li>Optionally an LLM rewrite of that heuristic, when a client is configured and a key is
 *       present. Failures fall back to the heuristic; the scan never dies because OpenAI is down.</li>
 * </ol>
 */
public final class ResponsibilityInferrer {
    private final LlmClient llm;

    public ResponsibilityInferrer(LlmClient llm) {
        this.llm = llm == null ? LlmClient.disabled() : llm;
    }

    public void infer(FileOutline outline, FileRank rank, DependencyGraph graph, KnowledgeGraph knowledge,
                      String architecture, boolean allowLlm) {
        String heuristic = heuristic(outline, rank);
        if (!allowLlm || !llm.available() || rank == null || !rank.getLevel().name().equals("DEEP")) {
            outline.setResponsibility(heuristic, outline.primaryJavadoc().isEmpty() ? "heuristic" : "javadoc");
            return;
        }
        String rewritten = llm.complete(systemPrompt(), userPrompt(outline, rank, graph, knowledge, architecture, heuristic));
        if (rewritten == null || rewritten.trim().isEmpty()) {
            outline.setResponsibility(heuristic, outline.primaryJavadoc().isEmpty() ? "heuristic" : "javadoc");
            return;
        }
        outline.setResponsibility(firstLine(rewritten), "llm");
    }

    static String heuristic(FileOutline outline, FileRank rank) {
        String doc = outline.primaryJavadoc();
        if (!doc.isEmpty()) return doc;

        List<String> annotations = new ArrayList<>();
        for (DeclaredSymbol symbol : outline.getSymbols()) annotations.addAll(symbol.getAnnotations());
        String joined = String.join(" ", annotations).toLowerCase(Locale.ROOT);
        String typeName = outline.typeNames().isEmpty() ? fileStem(outline.getFile()) : outline.typeNames().get(0);
        String lower = typeName.toLowerCase(Locale.ROOT);

        if (rank != null && rank.isEntryPoint() && (joined.contains("springbootapplication") || lower.contains("application")
                || lower.contains("main") || "main".equals(lower))) {
            return "Starts the application.";
        }
        if (joined.contains("restcontroller") || joined.contains("controller") || endsWith(lower, "controller")) {
            return "Handles HTTP requests for " + humanize(typeName, "Controller") + ".";
        }
        if (joined.contains("service") || endsWith(lower, "service")) {
            return "Implements application logic for " + humanize(typeName, "Service") + ".";
        }
        if (joined.contains("repository") || endsWith(lower, "repository") || endsWith(lower, "dao")) {
            return "Persists and loads " + humanize(typeName, "Repository", "Dao") + ".";
        }
        if (endsWith(lower, "validator")) {
            return "Validates " + humanize(typeName, "Validator") + ".";
        }
        if (endsWith(lower, "config") || endsWith(lower, "configuration")) {
            return "Configures " + humanize(typeName, "Config", "Configuration") + ".";
        }
        if (endsWith(lower, "entity") || endsWith(lower, "model") || endsWith(lower, "dto")) {
            return "Data model for " + humanize(typeName, "Entity", "Model", "Dto") + ".";
        }
        if (rank != null && rank.isEntryPoint()) {
            return "Entry point of the system.";
        }
        if (!outline.types().isEmpty()) {
            DeclaredSymbol type = outline.types().get(0);
            if (!type.getParents().isEmpty()) {
                return type.getName() + " " + type.getKind().name().toLowerCase(Locale.ROOT)
                        + " extending " + String.join(", ", type.getParents()) + ".";
            }
            return type.getName() + " " + type.getKind().name().toLowerCase(Locale.ROOT) + ".";
        }
        if (!outline.methodNames().isEmpty()) {
            return "Defines " + String.join(", ", take(outline.methodNames(), 4)) + ".";
        }
        return "Source file " + outline.getFile().getFilePath().getFileName() + ".";
    }

    static String systemPrompt() {
        return "You write one-sentence file responsibilities for software documentation. "
                + "Say what the file is for, not how it is implemented. "
                + "Return only that sentence, no quotes and no preamble.";
    }

    static String userPrompt(FileOutline outline, FileRank rank, DependencyGraph graph,
                             KnowledgeGraph knowledge, String architecture, String heuristic) {
        StringBuilder sb = new StringBuilder();
        sb.append("Architecture: ").append(architecture == null ? "Unknown" : architecture).append('\n');
        sb.append("File: ").append(graph == null ? outline.getFile().getFilePath().getFileName()
                : graph.relativePath(outline.getFile())).append('\n');
        sb.append("Language: ").append(outline.getFile().getLanguage()).append('\n');
        sb.append("Level: ").append(outline.getLevel()).append('\n');
        if (rank != null) {
            sb.append("Entry point: ").append(rank.isEntryPoint()).append('\n');
        }
        sb.append("Types: ").append(String.join(", ", outline.typeNames())).append('\n');
        sb.append("Methods: ").append(String.join(", ", take(outline.methodNames(), 12))).append('\n');
        sb.append("Annotations: ");
        List<String> annotations = new ArrayList<>();
        for (DeclaredSymbol symbol : outline.getSymbols()) annotations.addAll(symbol.getAnnotations());
        sb.append(String.join(", ", annotations)).append('\n');
        if (knowledge != null) {
            List<String> used = new ArrayList<>();
            for (FileInfo target : knowledge.uses(outline.getFile())) {
                used.add(target.getFilePath().getFileName().toString());
            }
            sb.append("Uses files: ").append(String.join(", ", used)).append('\n');
        }
        sb.append("Existing comment: ").append(outline.primaryJavadoc()).append('\n');
        sb.append("Heuristic: ").append(heuristic).append('\n');
        return sb.toString();
    }

    private static String firstLine(String text) {
        String trimmed = text.trim();
        int nl = trimmed.indexOf('\n');
        String line = nl < 0 ? trimmed : trimmed.substring(0, nl).trim();
        if (line.startsWith("\"") && line.endsWith("\"") && line.length() > 1) {
            line = line.substring(1, line.length() - 1);
        }
        return line;
    }

    private static boolean endsWith(String lower, String suffix) {
        return lower.endsWith(suffix);
    }

    static String humanize(String typeName, String... suffixes) {
        String stem = typeName;
        for (String suffix : suffixes) {
            if (stem.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))
                    && stem.length() > suffix.length()) {
                stem = stem.substring(0, stem.length() - suffix.length());
                break;
            }
        }
        if (stem.isEmpty()) return "this module";
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) words.append(' ');
            words.append(Character.toLowerCase(c));
        }
        return words.toString().trim();
    }

    private static String fileStem(FileInfo file) {
        String name = file.getFilePath().getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private static List<String> take(List<String> values, int n) {
        return values.size() <= n ? values : values.subList(0, n);
    }
}
