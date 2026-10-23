package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between Rust files of the same Cargo workspace.
 *
 * <p><b>Module paths.</b> Inside a crate, a file's module path follows from its location under
 * {@code src/}: {@code src/lib.rs} and {@code src/main.rs} are the crate root,
 * {@code src/net/mod.rs} and {@code src/net.rs} are {@code net}, {@code src/net/tcp.rs} is
 * {@code net::tcp}. Every file is indexed by crate and module path.
 *
 * <p><b>Edges</b> come from:
 * <ul>
 *   <li>{@code mod x;} - the child module file of the current module,</li>
 *   <li>{@code use} trees such as {@code use crate::net::{tcp::Conn, udp};}, expanded into paths,</li>
 *   <li>paths written inline in code: {@code crate::net::tcp::connect()}, {@code super::Config}.</li>
 * </ul>
 * A path starts at {@code crate} (crate root), {@code self} (current module), {@code super}
 * (parent; repeatable), the name of another crate in the workspace, or - Rust 2018 style - a
 * child of the current module. Trailing segments are dropped until a module is found, so
 * {@code crate::net::tcp::Conn} resolves to the file of {@code net::tcp}.
 */
final class RustDependencyExtractor implements DependencyExtractor {
    private static final Pattern MOD_DECL = Pattern.compile("(?m)^\\s*(?:pub(?:\\([^)]*\\))?\\s+)?mod\\s+(\\w+)\\s*;");
    private static final Pattern USE = Pattern.compile("(?m)^\\s*(?:pub(?:\\([^)]*\\))?\\s+)?use\\s+([^;]+);");
    private static final Pattern INLINE_PATH = Pattern.compile("\\b(?:crate|super|self)(?:::\\w+)+");
    private static final Pattern PACKAGE_NAME = Pattern.compile("(?ms)^\\[package\\].*?^name\\s*=\\s*\"([^\"]+)\"");

    /** One Cargo package: its src directory and module path -> file. */
    private static final class Crate {
        final Path srcDir;
        final Map<String, FileInfo> modules = new HashMap<>();

        Crate(Path srcDir) {
            this.srcDir = srcDir;
        }
    }

    private final SourceSet sources;
    private final Map<Path, Crate> cratesByDir = new HashMap<>();
    private final Map<String, Crate> cratesByName = new HashMap<>();
    private final Map<FileInfo, Crate> crateOf = new HashMap<>();
    private final Map<FileInfo, List<String>> modulePathOf = new HashMap<>();
    private final Map<Path, Path> cargoDirCache = new HashMap<>();

    RustDependencyExtractor(SourceSet sources, Collection<FileInfo> files) {
        this.sources = sources;
        for (FileInfo file : files) {
            Path path = SourceSet.normalize(file.getFilePath());
            Path cargoDir = findCargoDir(path.getParent());
            if (cargoDir == null || !path.startsWith(cargoDir.resolve("src"))) continue;
            Crate crate = cratesByDir.computeIfAbsent(cargoDir, this::loadCrate);
            List<String> modulePath = modulePath(crate.srcDir.relativize(path));
            crate.modules.putIfAbsent(String.join("::", modulePath), file);
            crateOf.put(file, crate);
            modulePathOf.put(file, modulePath);
        }
    }

    private Crate loadCrate(Path cargoDir) {
        Crate crate = new Crate(cargoDir.resolve("src"));
        try {
            String manifest = new String(Files.readAllBytes(cargoDir.resolve("Cargo.toml")), StandardCharsets.UTF_8);
            Matcher name = PACKAGE_NAME.matcher(manifest);
            if (name.find()) {
                // Crate names use '-' in Cargo.toml but '_' in code.
                cratesByName.put(name.group(1).replace('-', '_'), crate);
            }
        } catch (IOException ignored) {
            // Without a readable name the crate is still usable through crate:: paths.
        }
        return crate;
    }

    /** {@code lib.rs} -> []; {@code net/mod.rs} -> [net]; {@code net/tcp.rs} -> [net, tcp]. */
    static List<String> modulePath(Path relativeToSrc) {
        List<String> segments = new ArrayList<>();
        for (Path p : relativeToSrc) segments.add(p.toString());
        String last = segments.remove(segments.size() - 1).replaceAll("\\.rs$", "");
        boolean crateRoot = segments.isEmpty() && (last.equals("lib") || last.equals("main"));
        if (!crateRoot && !last.equals("mod")) {
            segments.add(last);
        }
        return segments;
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        Crate crate = crateOf.get(file);
        if (crate == null) {
            return Collections.emptyList();
        }
        String code = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.RUST);
        LineIndex lines = new LineIndex(code);
        List<String> current = modulePathOf.get(file);
        DependencyCollector deps = new DependencyCollector(file);

        Matcher mod = MOD_DECL.matcher(code);
        while (mod.find()) {
            List<String> child = new ArrayList<>(current);
            child.add(mod.group(1));
            deps.add(crate.modules.get(String.join("::", child)), 1, lines.lineOf(mod.start()));
        }

        StringBuilder rest = new StringBuilder(code);
        Matcher use = USE.matcher(code);
        while (use.find()) {
            for (String path : expandUseTree(use.group(1).replaceAll("\\s+", ""))) {
                deps.add(resolvePath(path, crate, current), 1, lines.lineOf(use.start()));
            }
            SourceScanner.blankRange(rest, use.start(), use.end());
        }

        Matcher inline = INLINE_PATH.matcher(rest);
        while (inline.find()) {
            deps.add(resolvePath(inline.group(), crate, current), 1, lines.lineOf(inline.start()));
        }
        return deps.toList();
    }

    /** {@code a::{b, c::{d, e as f}}} -> [a::b, a::c::d, a::c::e]; {@code a::*} -> [a]. */
    static List<String> expandUseTree(String tree) {
        List<String> result = new ArrayList<>();
        int brace = tree.indexOf('{');
        if (brace < 0) {
            String path = tree.replaceAll("\\bas\\b.*$", "").replaceAll("::\\*$", "");
            if (!path.isEmpty()) result.add(path);
            return result;
        }
        String prefix = tree.substring(0, brace);
        String inner = tree.substring(brace + 1, tree.lastIndexOf('}'));
        int depth = 0;
        int start = 0;
        for (int i = 0; i <= inner.length(); i++) {
            char c = i < inner.length() ? inner.charAt(i) : ',';
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (c == ',' && depth == 0) {
                String item = inner.substring(start, i);
                start = i + 1;
                if (item.isEmpty()) continue;
                if (item.equals("self")) {
                    result.add(prefix.replaceAll("::$", ""));
                } else {
                    result.addAll(expandUseTree(prefix + item));
                }
            }
        }
        return result;
    }

    private FileInfo resolvePath(String path, Crate crate, List<String> current) {
        List<String> segments = new ArrayList<>(Arrays.asList(path.replaceAll("\\s+as\\s+\\w+$", "").split("::")));
        Crate target = crate;
        List<String> start;
        // A path anchored at crate/self/super/<crate name> may resolve to the anchor module itself
        // (use crate::Config, where Config lives in lib.rs). A bare path may not: "std::fmt" must
        // not fall back to the crate root just because "std" is unknown.
        boolean anchored = true;
        String first = segments.get(0);
        if (first.equals("crate")) {
            segments.remove(0);
            start = new ArrayList<>();
        } else if (first.equals("self") || first.equals("super")) {
            start = new ArrayList<>(current);
            while (!segments.isEmpty() && (segments.get(0).equals("super") || segments.get(0).equals("self"))) {
                if (segments.remove(0).equals("super") && !start.isEmpty()) start.remove(start.size() - 1);
            }
        } else if (cratesByName.containsKey(first)) {
            target = cratesByName.get(first);
            segments.remove(0);
            start = new ArrayList<>();
        } else {
            anchored = false;
            start = new ArrayList<>(current);
        }

        FileInfo found = longestModulePrefix(target, start, segments, anchored);
        if (found == null && !anchored) {
            // Rust 2018: inside the crate root, "use net::tcp" names a top-level module.
            found = longestModulePrefix(target, new ArrayList<>(), segments, false);
        }
        return found;
    }

    /** Longest {@code start + segments[0..n]} that is a module of {@code crate}; n = 0 only if {@code allowStartItself}. */
    private static FileInfo longestModulePrefix(Crate crate, List<String> start, List<String> segments,
                                                boolean allowStartItself) {
        for (int len = segments.size(); len >= (allowStartItself ? 0 : 1); len--) {
            List<String> candidate = new ArrayList<>(start);
            candidate.addAll(segments.subList(0, len));
            FileInfo file = crate.modules.get(String.join("::", candidate));
            if (file != null) {
                return file;
            }
        }
        return null;
    }

    private Path findCargoDir(Path dir) {
        if (dir == null || !dir.startsWith(sources.root())) return null;
        if (cargoDirCache.containsKey(dir)) return cargoDirCache.get(dir);
        Path result = Files.isRegularFile(dir.resolve("Cargo.toml")) ? dir : findCargoDir(dir.getParent());
        cargoDirCache.put(dir, result);
        return result;
    }
}
