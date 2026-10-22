package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds file-to-file dependencies between Java sources of the same repository.
 *
 * <p>Three sources of edges:
 * <ul>
 *   <li>explicit imports ({@code import a.b.Foo;}, nested and static imports resolve to the outer file)</li>
 *   <li>wildcard imports ({@code import a.b.*;}) - only classes actually mentioned in the body</li>
 *   <li>same-package usage, which Java allows without any import</li>
 * </ul>
 * The reference count of an edge is how often the target's simple name appears in the body.
 */
final class JavaDependencyExtractor {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+(static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;");

    private final Map<FileInfo, String> cleanedSources = new HashMap<>();
    private final Map<FileInfo, String> packageOf = new HashMap<>();
    private final Map<String, FileInfo> classesByFqn = new HashMap<>();
    private final Map<String, List<FileInfo>> classesByPackage = new HashMap<>();

    JavaDependencyExtractor(Map<FileInfo, String> sources) {
        sources.forEach((file, source) -> {
            String cleaned = JavaSourceScanner.blankCommentsAndLiterals(source);
            cleanedSources.put(file, cleaned);
            Matcher m = PACKAGE.matcher(cleaned);
            String pkg = m.find() ? m.group(1) : "";
            packageOf.put(file, pkg);
            String fqn = pkg.isEmpty() ? simpleName(file) : pkg + "." + simpleName(file);
            classesByFqn.put(fqn, file);
            classesByPackage.computeIfAbsent(pkg, k -> new ArrayList<>()).add(file);
        });
    }

    /** Declared package, or empty string for the default package. */
    String packageName(FileInfo file) {
        return packageOf.getOrDefault(file, "");
    }

    List<RawDependency> extract(FileInfo file) {
        String cleaned = cleanedSources.get(file);
        if (cleaned == null) {
            return new ArrayList<>();
        }
        LineIndex lines = new LineIndex(cleaned);
        Map<String, int[]> tokens = JavaSourceScanner.countIdentifiers(JavaSourceScanner.blankDirectives(cleaned));
        Map<FileInfo, int[]> found = new LinkedHashMap<>();

        Matcher m = IMPORT.matcher(cleaned);
        while (m.find()) {
            String name = m.group(2);
            boolean wildcard = m.group(3) != null;
            int importLine = lines.lineOf(m.start(2));
            if (wildcard && classesByPackage.containsKey(name)) {
                for (FileInfo target : classesByPackage.get(name)) {
                    addIfUsed(found, file, target, tokens);
                }
                continue;
            }
            FileInfo target = resolveClass(name);
            if (target != null && !target.equals(file) && !found.containsKey(target)) {
                int[] usage = tokens.get(simpleName(target));
                int count = usage == null ? 1 : Math.max(1, usage[0]);
                found.put(target, new int[]{count, importLine});
            }
        }

        for (FileInfo sibling : classesByPackage.getOrDefault(packageName(file), new ArrayList<>())) {
            addIfUsed(found, file, sibling, tokens);
        }

        List<RawDependency> result = new ArrayList<>();
        found.forEach((target, v) -> result.add(new RawDependency(file, target, v[0], v[1])));
        return result;
    }

    private void addIfUsed(Map<FileInfo, int[]> found, FileInfo file, FileInfo target, Map<String, int[]> tokens) {
        if (target.equals(file) || found.containsKey(target)) {
            return;
        }
        int[] usage = tokens.get(simpleName(target));
        if (usage != null && usage[0] > 0) {
            found.put(target, new int[]{usage[0], usage[1]});
        }
    }

    /** Resolves {@code a.b.Outer.Inner} or {@code a.b.Util.method} to the file declaring {@code a.b.Outer}. */
    private FileInfo resolveClass(String name) {
        String candidate = name;
        while (true) {
            FileInfo file = classesByFqn.get(candidate);
            if (file != null) {
                return file;
            }
            int dot = candidate.lastIndexOf('.');
            if (dot < 0) {
                return null;
            }
            candidate = candidate.substring(0, dot);
        }
    }

    static String simpleName(FileInfo file) {
        String fileName = file.getFilePath().getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
