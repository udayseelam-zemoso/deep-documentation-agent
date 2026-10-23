package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between JavaScript/TypeScript files, read from module specifiers in
 * {@code import ... from 'x'}, {@code export ... from 'x'}, side-effect {@code import 'x'},
 * {@code require('x')} and dynamic {@code import('x')}.
 *
 * <p>A specifier is turned into a file the way bundlers do: try the exact path, then the path
 * plus each known extension, then {@code <path>/index.<ext>}. TypeScript ESM code writes
 * {@code './x.js'} for a file that is {@code x.ts} on disk, so that rewrite is tried too.
 * Only relative specifiers are resolved; bare specifiers ({@code 'react'}) are npm packages.
 * Each statement counts as one reference.
 */
final class JsDependencyExtractor implements DependencyExtractor {
    private static final List<Pattern> PATTERNS = Arrays.asList(
            Pattern.compile("\\b(?:import|export)\\s+(?:type\\s+)?[\\w*{}\\s,$]*?\\s*from\\s*['\"]([^'\"\\n]+)['\"]"),
            Pattern.compile("\\bimport\\s*['\"]([^'\"\\n]+)['\"]"),
            Pattern.compile("\\brequire\\s*\\(\\s*['\"]([^'\"\\n]+)['\"]\\s*\\)"),
            Pattern.compile("\\bimport\\s*\\(\\s*['\"]([^'\"\\n]+)['\"]\\s*\\)")
    );
    static final List<String> EXTENSIONS = Arrays.asList(".ts", ".tsx", ".js", ".jsx", ".mjs", ".cjs");

    private final SourceSet sources;

    JsDependencyExtractor(SourceSet sources) {
        this.sources = sources;
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String code = SourceScanner.blankComments(sources.source(file), SourceScanner.Syntax.JS);
        LineIndex lines = new LineIndex(code);
        // Keyed by offset so a statement matched by two patterns is only counted once.
        Map<Integer, String> specifiers = new TreeMap<>();
        for (Pattern pattern : PATTERNS) {
            Matcher m = pattern.matcher(code);
            while (m.find()) {
                specifiers.putIfAbsent(m.start(1), m.group(1));
            }
        }

        DependencyCollector deps = new DependencyCollector(file);
        specifiers.forEach((offset, spec) -> deps.add(resolve(file, spec), 1, lines.lineOf(offset)));
        return deps.toList();
    }

    FileInfo resolve(FileInfo from, String spec) {
        if (!spec.startsWith(".")) {
            return null;
        }
        return resolveFile(SourceSet.normalize(from.getFilePath()).getParent().resolve(spec).normalize());
    }

    /** Applies the bundler lookup rules to an absolute path without extension guessing done yet. */
    FileInfo resolveFile(Path base) {
        List<Path> candidates = new ArrayList<>();
        candidates.add(base);
        String baseName = base.getFileName() == null ? "" : base.getFileName().toString();
        if (baseName.endsWith(".js") || baseName.endsWith(".jsx") || baseName.endsWith(".mjs")) {
            String stem = baseName.substring(0, baseName.lastIndexOf('.'));
            candidates.add(base.resolveSibling(stem + ".ts"));
            candidates.add(base.resolveSibling(stem + ".tsx"));
            candidates.add(base.resolveSibling(stem + ".mts"));
        }
        for (String ext : EXTENSIONS) {
            candidates.add(base.resolveSibling(baseName + ext));
        }
        for (String ext : EXTENSIONS) {
            candidates.add(base.resolve("index" + ext));
        }
        for (Path candidate : candidates) {
            FileInfo target = sources.at(candidate);
            if (target != null) {
                return target;
            }
        }
        return null;
    }
}
