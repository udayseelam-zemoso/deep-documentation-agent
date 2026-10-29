package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between Python modules from {@code import} and {@code from ... import}.
 *
 * <p><b>Module names.</b> Every file gets a dotted name from its path ({@code app/core/db.py}
 * is {@code app.core.db}, {@code app/core/__init__.py} is {@code app.core}). Projects put their
 * import root in different places ({@code src/}, the repo root, a sub-project), so a module is
 * registered under every suffix of its name and {@code import core.db} finds it from any root.
 * When several modules share a suffix, the one closest to the importing file wins.
 *
 * <p><b>Relative imports</b> ({@code from ..models import User}) are resolved from the importing
 * file's package, one level up per extra dot.
 *
 * <p>For {@code from pkg import name}, {@code pkg/name.py} is tried first (a submodule), then
 * {@code pkg} itself (a function or class defined in it). The reference count is how often the
 * bound name is used in the file, at least 1.
 */
final class PythonDependencyExtractor implements DependencyExtractor {
    /** import a.b.c [as d][, e.f [as g]] */
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^[ \\t]*import[ \\t]+([\\w.]+(?:[ \\t]+as[ \\t]+\\w+)?(?:[ \\t]*,[ \\t]*[\\w.]+(?:[ \\t]+as[ \\t]+\\w+)?)*)");
    /** from [dots]module import names | (names, ...) | * */
    private static final Pattern FROM_IMPORT = Pattern.compile(
            "(?m)^[ \\t]*from[ \\t]+(\\.*)([\\w.]*)[ \\t]+import[ \\t]+(\\([^)]*\\)|[^\\n]*)");

    private final SourceSet sources;
    /** Every dotted suffix of every module name -> modules with that suffix. */
    private final Map<String, List<FileInfo>> modulesBySuffix = new HashMap<>();

    PythonDependencyExtractor(SourceSet sources, Collection<FileInfo> files) {
        this.sources = sources;
        for (FileInfo file : files) {
            List<String> segments = moduleSegments(sources.relativePath(file));
            for (int i = 0; i < segments.size(); i++) {
                String suffix = String.join(".", segments.subList(i, segments.size()));
                modulesBySuffix.computeIfAbsent(suffix, k -> new ArrayList<>()).add(file);
            }
        }
    }

    /** {@code app/core/db.py} -> [app, core, db]; {@code app/core/__init__.py} -> [app, core]. */
    static List<String> moduleSegments(String relativePath) {
        String withoutExtension = relativePath.replaceAll("\\.pyi?$|\\.pyw$", "");
        List<String> segments = new ArrayList<>(Arrays.asList(withoutExtension.split("/")));
        if (!segments.isEmpty() && segments.get(segments.size() - 1).equals("__init__")) {
            segments.remove(segments.size() - 1);
        }
        return segments;
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String code = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.PYTHON);
        LineIndex lines = new LineIndex(code);
        StringBuilder body = new StringBuilder(code);
        List<Object[]> imports = new ArrayList<>(); // {target, boundName, line}

        Matcher m = IMPORT.matcher(code);
        while (m.find()) {
            int line = lines.lineOf(m.start());
            for (String part : m.group(1).split(",")) {
                String[] words = part.trim().split("\\s+as\\s+");
                String module = words[0].trim();
                String bound = words.length > 1 ? words[1].trim() : module.substring(module.lastIndexOf('.') + 1);
                imports.add(new Object[]{resolveAbsolute(module, file), bound, line});
            }
            SourceScanner.blankRange(body, m.start(), m.end());
        }

        Matcher f = FROM_IMPORT.matcher(code);
        while (f.find()) {
            int line = lines.lineOf(f.start());
            int level = f.group(1).length();
            String module = f.group(2);
            String names = f.group(3).replace("(", " ").replace(")", " ");
            for (String part : names.split(",")) {
                String[] words = part.trim().split("\\s+as\\s+");
                String name = words[0].trim();
                if (name.isEmpty()) continue;
                String bound = words.length > 1 ? words[1].trim() : name;
                FileInfo target = null;
                if (!name.equals("*")) {
                    target = resolve(level, join(module, name), file);
                }
                if (target == null) {
                    target = resolve(level, module, file);
                }
                imports.add(new Object[]{target, bound, line});
            }
            SourceScanner.blankRange(body, f.start(), f.end());
        }

        Map<String, int[]> tokens = SourceScanner.countIdentifiers(body.toString());
        DependencyCollector deps = new DependencyCollector(file);
        for (Object[] imp : imports) {
            int uses = SourceScanner.count(tokens, (String) imp[1]);
            deps.add((FileInfo) imp[0], Math.max(1, uses), (Integer) imp[2]);
        }
        return deps.toList();
    }

    private FileInfo resolve(int level, String module, FileInfo from) {
        return level == 0 ? resolveAbsolute(module, from) : resolveRelative(level, module, from);
    }

    private FileInfo resolveAbsolute(String module, FileInfo from) {
        if (module.isEmpty()) return null;
        List<FileInfo> candidates = modulesBySuffix.get(module);
        if (candidates == null) return null;
        FileInfo best = null;
        int bestShared = -1;
        for (FileInfo candidate : candidates) {
            if (candidate.equals(from)) continue;
            int shared = SourceSet.sharedDirectories(from, candidate);
            if (shared > bestShared) {
                best = candidate;
                bestShared = shared;
            }
        }
        return best;
    }

    /** {@code from ..x.y import z} in {@code a/b/c.py}: level 2 starts at {@code a/}. */
    private FileInfo resolveRelative(int level, String module, FileInfo from) {
        Path base = SourceSet.normalize(from.getFilePath()).getParent();
        for (int i = 1; i < level && base != null; i++) {
            base = base.getParent();
        }
        if (base == null) return null;
        Path target = module.isEmpty() ? base : base.resolve(module.replace('.', '/'));
        FileInfo file = sources.at(target.resolveSibling(target.getFileName() + ".py"));
        return file != null ? file : sources.at(target.resolve("__init__.py"));
    }

    private static String join(String module, String name) {
        return module.isEmpty() ? name : module + "." + name;
    }
}
