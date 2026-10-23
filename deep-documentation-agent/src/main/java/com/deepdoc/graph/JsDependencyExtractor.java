package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
 * Relative specifiers ({@code ./x}) resolve against the importing file; everything else goes
 * through {@link JsAliasResolver} (tsconfig paths, workspace packages, configured aliases).
 * Each statement counts as one reference.
 *
 * <p>Vue and Svelte single-file components are handled by reading only their
 * {@code <script>} blocks; the template and style sections are blanked out.
 */
final class JsDependencyExtractor implements DependencyExtractor {
    private static final Pattern SCRIPT_BLOCK =
            Pattern.compile("(?is)<script\\b[^>]*>(.*?)</script\\s*>");
    private static final List<Pattern> PATTERNS = Arrays.asList(
            Pattern.compile("\\b(?:import|export)\\s+(?:type\\s+)?[\\w*{}\\s,$]*?\\s*from\\s*['\"]([^'\"\\n]+)['\"]"),
            Pattern.compile("\\bimport\\s*['\"]([^'\"\\n]+)['\"]"),
            Pattern.compile("\\brequire\\s*\\(\\s*['\"]([^'\"\\n]+)['\"]\\s*\\)"),
            Pattern.compile("\\bimport\\s*\\(\\s*['\"]([^'\"\\n]+)['\"]\\s*\\)")
    );
    static final List<String> EXTENSIONS =
            Arrays.asList(".ts", ".tsx", ".mts", ".js", ".jsx", ".mjs", ".cjs", ".vue", ".svelte");

    private final SourceSet sources;
    private final JsAliasResolver aliases;

    JsDependencyExtractor(SourceSet sources, Collection<FileInfo> files, Map<String, String> configuredAliases) {
        this.sources = sources;
        List<Path> paths = new ArrayList<>();
        files.forEach(f -> paths.add(f.getFilePath()));
        this.aliases = new JsAliasResolver(sources.root(), paths, configuredAliases);
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String source = sources.source(file);
        if ("Vue".equals(file.getLanguage()) || "Svelte".equals(file.getLanguage())) {
            source = scriptBlocksOnly(source);
        }
        String code = SourceScanner.blankComments(source, SourceScanner.Syntax.JS);
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
        if (spec.startsWith(".")) {
            return resolveFile(SourceSet.normalize(from.getFilePath()).getParent().resolve(spec).normalize());
        }
        for (Path candidate : aliases.candidates(from.getFilePath(), spec)) {
            FileInfo target = resolveFile(candidate);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    /** Keeps the contents of {@code <script>} blocks and blanks everything else, preserving line numbers. */
    static String scriptBlocksOnly(String source) {
        StringBuilder out = new StringBuilder(source);
        SourceScanner.blankRange(out, 0, source.length());
        Matcher m = SCRIPT_BLOCK.matcher(source);
        while (m.find()) {
            out.replace(m.start(1), m.end(1), m.group(1));
        }
        return out.toString();
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
