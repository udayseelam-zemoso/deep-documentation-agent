package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds dependencies between JavaScript/TypeScript files from ES module imports,
 * re-exports, {@code require(...)} and dynamic {@code import(...)}.
 *
 * <p>Only relative specifiers ({@code ./x}, {@code ../x}) are resolved; bare specifiers are
 * npm packages, and path aliases such as {@code @/x} are not resolved yet.
 */
final class JsDependencyExtractor {
    private static final List<Pattern> PATTERNS = Arrays.asList(
            Pattern.compile("\\b(?:import|export)\\s+(?:type\\s+)?[\\w*{}\\s,$]*?\\s*from\\s*['\"]([^'\"\\n]+)['\"]"),
            Pattern.compile("\\bimport\\s*['\"]([^'\"\\n]+)['\"]"),
            Pattern.compile("\\brequire\\s*\\(\\s*['\"]([^'\"\\n]+)['\"]\\s*\\)"),
            Pattern.compile("\\bimport\\s*\\(\\s*['\"]([^'\"\\n]+)['\"]\\s*\\)")
    );
    private static final List<String> EXTENSIONS = Arrays.asList(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs");

    private final Map<Path, FileInfo> filesByPath;

    JsDependencyExtractor(Map<Path, FileInfo> filesByPath) {
        this.filesByPath = filesByPath;
    }

    List<RawDependency> extract(FileInfo file, String source) {
        LineIndex lines = new LineIndex(source);
        // Keyed by match offset so overlapping patterns never count the same statement twice.
        Map<Integer, String> specifiers = new TreeMap<>();
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(source);
            while (m.find()) {
                specifiers.putIfAbsent(m.start(1), m.group(1));
            }
        }

        Map<FileInfo, int[]> found = new LinkedHashMap<>();
        specifiers.forEach((offset, spec) -> {
            FileInfo target = resolve(file.getFilePath(), spec);
            if (target == null || target.equals(file)) {
                return;
            }
            int[] entry = found.get(target);
            if (entry == null) {
                found.put(target, new int[]{1, lines.lineOf(offset)});
            } else {
                entry[0]++;
            }
        });

        List<RawDependency> result = new ArrayList<>();
        found.forEach((target, v) -> result.add(new RawDependency(file, target, v[0], v[1])));
        return result;
    }

    private FileInfo resolve(Path from, String spec) {
        if (!spec.startsWith(".")) {
            return null;
        }
        Path base = from.toAbsolutePath().getParent().resolve(spec).normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(base);
        String baseName = base.getFileName() == null ? "" : base.getFileName().toString();
        if (baseName.endsWith(".js") || baseName.endsWith(".jsx")) {
            // TypeScript ESM code imports "./x.js" while the file on disk is x.ts
            String stem = baseName.substring(0, baseName.lastIndexOf('.'));
            candidates.add(base.resolveSibling(stem + ".ts"));
            candidates.add(base.resolveSibling(stem + ".tsx"));
        }
        for (String ext : EXTENSIONS) {
            candidates.add(base.resolveSibling(baseName + ext));
        }
        for (String ext : EXTENSIONS) {
            candidates.add(base.resolve("index" + ext));
        }
        for (Path candidate : candidates) {
            FileInfo target = filesByPath.get(candidate);
            if (target != null) {
                return target;
            }
        }
        return null;
    }
}
