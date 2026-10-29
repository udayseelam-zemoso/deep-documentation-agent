package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between C# files.
 *
 * <p>C# {@code using} directives import whole namespaces, and one file may declare many types
 * (or part of one, with {@code partial}). So every declared type is indexed under its namespace,
 * and a file depends on another when it mentions a type that the other file declares and that
 * is visible to it. Visible means declared in:
 * <ul>
 *   <li>the file's own namespace or any enclosing one ({@code A.B.C} also sees {@code A.B} and {@code A}),</li>
 *   <li>a namespace named in a {@code using} directive of the file,</li>
 *   <li>a namespace named in a {@code global using} anywhere in the repository.</li>
 * </ul>
 * {@code using static A.B.Util;} and {@code using Alias = A.B.Type;} point at a single type.
 * Fully qualified names in code ({@code A.B.Type}) are resolved as well.
 */
final class CSharpDependencyExtractor implements DependencyExtractor {
    private static final Pattern NAMESPACE = Pattern.compile("\\bnamespace\\s+([\\w.]+)");
    private static final Pattern USING = Pattern.compile(
            "(?m)^\\s*(global\\s+)?using\\s+(static\\s+)?(?:(\\w+)\\s*=\\s*)?([\\w.]+)\\s*;");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|struct|enum|record)\\s+([A-Za-z_]\\w*)");
    private static final Pattern QUALIFIED_NAME = Pattern.compile("\\b[A-Z]\\w*(?:\\.[A-Za-z_]\\w*)+");
    /** "record struct X" and "where T : class" must not register "struct"/"class" as type names. */
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList("class", "struct", "interface", "enum", "record"));

    private final Map<FileInfo, String> code = new HashMap<>();
    private final Map<FileInfo, String> namespaceOf = new HashMap<>();
    /** namespace -> type name -> files declaring it (several for partial types) */
    private final Map<String, Map<String, List<FileInfo>>> typesByNamespace = new HashMap<>();
    private final Set<String> globalUsings = new LinkedHashSet<>();

    CSharpDependencyExtractor(SourceSet sources, Collection<FileInfo> files) {
        for (FileInfo file : files) {
            String cleaned = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.C_LIKE);
            code.put(file, cleaned);
            Matcher ns = NAMESPACE.matcher(cleaned);
            String namespace = ns.find() ? ns.group(1) : "";
            namespaceOf.put(file, namespace);
            Matcher type = TYPE.matcher(cleaned);
            while (type.find()) {
                if (KEYWORDS.contains(type.group(1))) continue;
                typesByNamespace.computeIfAbsent(namespace, k -> new LinkedHashMap<>())
                        .computeIfAbsent(type.group(1), k -> new ArrayList<>()).add(file);
            }
            Matcher using = USING.matcher(cleaned);
            while (using.find()) {
                if (using.group(1) != null && using.group(2) == null && using.group(3) == null) {
                    globalUsings.add(using.group(4));
                }
            }
        }
    }

    @Override
    public String namespaceOf(FileInfo file) {
        return namespaceOf.get(file);
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String cleaned = code.get(file);
        LineIndex lines = new LineIndex(cleaned);
        String body = SourceScanner.blankLinesStartingWith(cleaned, "using", "global");
        Map<String, int[]> tokens = SourceScanner.countIdentifiers(body);
        DependencyCollector deps = new DependencyCollector(file);

        Set<String> visible = new LinkedHashSet<>(globalUsings);
        String own = namespaceOf.get(file);
        for (String ns = own; ; ns = ns.substring(0, ns.lastIndexOf('.'))) {
            visible.add(ns);
            if (ns.lastIndexOf('.') < 0) break;
        }
        visible.add("");

        Matcher using = USING.matcher(cleaned);
        while (using.find()) {
            String name = using.group(4);
            int line = lines.lineOf(using.start(4));
            if (using.group(2) != null || using.group(3) != null) {
                String usedAs = using.group(3) != null ? using.group(3) : name.substring(name.lastIndexOf('.') + 1);
                for (FileInfo target : resolveType(name)) {
                    deps.add(target, Math.max(1, SourceScanner.count(tokens, usedAs)), line);
                }
            } else {
                visible.add(name);
            }
        }

        for (String ns : visible) {
            for (Map.Entry<String, List<FileInfo>> type : typesByNamespace.getOrDefault(ns, new HashMap<>()).entrySet()) {
                int uses = SourceScanner.count(tokens, type.getKey());
                if (uses == 0 || type.getValue().contains(file) && type.getValue().size() == 1) continue;
                for (FileInfo target : type.getValue()) {
                    deps.addOnce(target, uses, SourceScanner.firstLine(tokens, type.getKey()));
                }
            }
        }

        Matcher qualified = QUALIFIED_NAME.matcher(body);
        while (qualified.find()) {
            for (FileInfo target : resolveType(qualified.group())) {
                deps.addOnce(target, 1, lines.lineOf(qualified.start()));
            }
        }
        return deps.toList();
    }

    /** {@code A.B.Type} or {@code A.B.Type.Member} -> files declaring {@code A.B.Type}. */
    private List<FileInfo> resolveType(String name) {
        String candidate = name;
        while (candidate.indexOf('.') > 0) {
            int dot = candidate.lastIndexOf('.');
            List<FileInfo> files = typesByNamespace.getOrDefault(candidate.substring(0, dot), new HashMap<>())
                    .get(candidate.substring(dot + 1));
            if (files != null) {
                return files;
            }
            candidate = candidate.substring(0, dot);
        }
        return new ArrayList<>();
    }
}
