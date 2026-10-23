package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Every file that takes part in the graph, with its source text and the lookups extractors need
 * to turn an import string into a file: by absolute path, by directory and by path suffix.
 */
final class SourceSet {
    private final Path root;
    private final Map<FileInfo, String> sources;
    private final Map<Path, FileInfo> byPath = new HashMap<>();
    private final Map<Path, List<FileInfo>> byDirectory = new HashMap<>();
    private final Map<String, List<FileInfo>> byFileName = new HashMap<>();

    SourceSet(Path root, Map<FileInfo, String> sources) {
        this.root = root.toAbsolutePath().normalize();
        this.sources = sources;
        for (FileInfo file : sources.keySet()) {
            Path path = normalize(file.getFilePath());
            byPath.put(path, file);
            byDirectory.computeIfAbsent(path.getParent(), k -> new ArrayList<>()).add(file);
            byFileName.computeIfAbsent(path.getFileName().toString(), k -> new ArrayList<>()).add(file);
        }
    }

    Path root() {
        return root;
    }

    Set<FileInfo> files() {
        return sources.keySet();
    }

    String source(FileInfo file) {
        return sources.get(file);
    }

    static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    /** The graph file at exactly this path, or null. */
    FileInfo at(Path path) {
        return byPath.get(normalize(path));
    }

    /** Graph files directly inside {@code directory} (not in sub-directories). */
    List<FileInfo> inDirectory(Path directory) {
        return byDirectory.getOrDefault(normalize(directory), Collections.emptyList());
    }

    /** Path relative to the repository root, always with forward slashes. */
    String relativePath(FileInfo file) {
        return root.relativize(normalize(file.getFilePath())).toString().replace('\\', '/');
    }

    /**
     * Finds a file whose repository-relative path ends with {@code suffix} on a directory
     * boundary ({@code "util/money.h"} matches {@code "src/util/money.h"} but not
     * {@code "src/myutil/money.h"}). Used when a language resolves imports against search paths
     * we do not know, e.g. C include directories or Ruby load paths.
     *
     * <p>If several files match, the one sharing the longest directory prefix with {@code near}
     * wins, then the shortest path, so imports prefer files close to the importing file.
     */
    FileInfo findBySuffix(String suffix, FileInfo near, Predicate<FileInfo> accept) {
        String normalized = suffix.replace('\\', '/');
        while (normalized.startsWith("./")) normalized = normalized.substring(2);
        if (normalized.isEmpty() || normalized.startsWith("../")) {
            return null;
        }
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        List<FileInfo> candidates = byFileName.getOrDefault(fileName, Collections.emptyList());
        FileInfo best = null;
        int bestShared = -1;
        for (FileInfo candidate : candidates) {
            if (candidate.equals(near) || !accept.test(candidate)) continue;
            String rel = relativePath(candidate);
            if (!rel.equals(normalized) && !rel.endsWith("/" + normalized)) continue;
            int shared = near == null ? 0 : sharedDirectories(near, candidate);
            if (best == null || shared > bestShared
                    || (shared == bestShared && rel.length() < relativePath(best).length())) {
                best = candidate;
                bestShared = shared;
            }
        }
        return best;
    }

    private int sharedDirectories(FileInfo a, FileInfo b) {
        Path pa = normalize(a.getFilePath()).getParent();
        Path pb = normalize(b.getFilePath()).getParent();
        int shared = 0;
        int max = Math.min(pa.getNameCount(), pb.getNameCount());
        while (shared < max && pa.getName(shared).equals(pb.getName(shared))) shared++;
        return shared;
    }
}
