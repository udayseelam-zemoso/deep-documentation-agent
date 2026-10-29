package com.deepdoc.graph;

import com.deepdoc.config.GraphSection;
import com.deepdoc.model.FileInfo;
import com.deepdoc.repository.CommitRecord;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Language-agnostic edges from Git history: files that keep being committed together are
 * probably coupled, even when no import between them can be seen.
 *
 * <p>Only used for files handled by the {@link GenericDependencyExtractor}, whose lexical
 * edges are the least reliable. A pair {@code (a, b)} is linked when
 * <ul>
 *   <li>they changed together in at least {@code coChangeMinCommits} commits, and</li>
 *   <li>the shared commits are at least {@code coChangeMinCoupling} of the changes of the
 *       less-often changed file, so a file touched in every commit does not couple to
 *       everything.</li>
 * </ul>
 * Commits touching more than {@code coChangeMaxFilesPerCommit} graph files (renames, formatting
 * sweeps) say nothing about coupling and are skipped. History cannot tell which file depends on
 * which, so the edge is added in both directions with a weight of one reference each, and only
 * where the code itself shows no edge between the two files.
 */
final class CoChangeEdges {
    private CoChangeEdges() {
    }

    /**
     * @param candidates files that may receive co-change edges (at least one end of each edge)
     * @param graphFiles all files in the graph, the possible other ends
     * @param workTree   Git work tree the commit paths are relative to
     */
    static List<RawDependency> find(Set<FileInfo> candidates, Collection<FileInfo> graphFiles,
                                    List<CommitRecord> commits, Path workTree, GraphSection config) {
        if (candidates.isEmpty() || commits.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, FileInfo> byGitPath = new HashMap<>();
        Path tree = workTree.toAbsolutePath().normalize();
        for (FileInfo file : graphFiles) {
            Path absolute = SourceSet.normalize(file.getFilePath());
            if (absolute.startsWith(tree)) {
                byGitPath.put(tree.relativize(absolute).toString().replace('\\', '/'), file);
            }
        }

        Map<FileInfo, Integer> changes = new HashMap<>();
        Map<FileInfo, Map<FileInfo, Integer>> together = new HashMap<>();
        for (CommitRecord commit : commits) {
            List<FileInfo> touched = new ArrayList<>();
            for (String path : commit.getPaths()) {
                FileInfo file = byGitPath.get(path);
                if (file != null) touched.add(file);
            }
            if (touched.size() > config.getCoChangeMaxFilesPerCommit()) continue;
            for (FileInfo file : touched) changes.merge(file, 1, Integer::sum);
            for (FileInfo a : touched) {
                if (!candidates.contains(a)) continue;
                for (FileInfo b : touched) {
                    if (!a.equals(b)) {
                        together.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
                    }
                }
            }
        }

        List<RawDependency> edges = new ArrayList<>();
        together.forEach((a, partners) -> partners.forEach((b, shared) -> {
            int fewerChanges = Math.min(changes.get(a), changes.get(b));
            boolean coupled = shared >= config.getCoChangeMinCommits()
                    && shared >= config.getCoChangeMinCoupling() * fewerChanges;
            // Pairs of two candidates are seen from both sides; emit each direction once.
            if (coupled) {
                edges.add(RawDependency.coChange(a, b));
                if (!candidates.contains(b)) edges.add(RawDependency.coChange(b, a));
            }
        }));
        return edges;
    }
}
