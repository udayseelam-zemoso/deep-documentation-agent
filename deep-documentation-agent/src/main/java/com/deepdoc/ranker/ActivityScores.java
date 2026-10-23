package com.deepdoc.ranker;

import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.PackageNode;
import com.deepdoc.model.FileInfo;
import com.deepdoc.repository.CommitRecord;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recency-weighted commit counts, the "activity" signal of the ranking. Files that change often
 * are where developers spend their time, so they benefit most from documentation.
 *
 * <p>A commit of age {@code t} days counts {@code 0.5^(t / halfLife)}: with the default
 * half-life of 180 days, a commit from today counts 1, one from six months ago 0.5, one from a
 * year ago 0.25. This keeps a burst of old activity (an initial import, a finished rewrite) from
 * outweighing current work.
 *
 * <p>A package counts each commit once, however many of its files the commit touched, so a
 * commit that edits 20 files in one package does not count 20 times for that package. Merge
 * commits and bulk commits are already filtered out by {@code GitInterface}.
 */
public final class ActivityScores {
    private static final double SECONDS_PER_DAY = 86_400;

    private final Map<FileInfo, Double> fileActivity;
    private final Map<PackageNode, Double> packageActivity;
    private final int relevantCommits;

    private ActivityScores(Map<FileInfo, Double> fileActivity, Map<PackageNode, Double> packageActivity,
                           int relevantCommits) {
        this.fileActivity = fileActivity;
        this.packageActivity = packageActivity;
        this.relevantCommits = relevantCommits;
    }

    /** No history: {@link #hasHistory()} is false and the ranker drops the activity terms. */
    public static ActivityScores none() {
        return new ActivityScores(new HashMap<>(), new HashMap<>(), 0);
    }

    /**
     * @param workTree Git work tree the commit paths are relative to; may be a parent of the
     *                 analyzed directory
     */
    public static ActivityScores fromCommits(DependencyGraph graph, List<CommitRecord> commits, Path workTree,
                                             double halfLifeDays, long nowSeconds) {
        // Commit paths are relative to the Git work tree, which may be above the analyzed
        // directory (one module of a monorepo), so map graph files to their work-tree path.
        Map<String, FileInfo> byGitPath = new HashMap<>();
        for (FileInfo file : graph.getFileGraph().vertexSet()) {
            Path absolute = file.getFilePath().toAbsolutePath().normalize();
            if (absolute.startsWith(workTree)) {
                byGitPath.put(workTree.relativize(absolute).toString().replace('\\', '/'), file);
            }
        }

        Map<FileInfo, Double> fileActivity = new HashMap<>();
        Map<PackageNode, Double> packageActivity = new HashMap<>();
        // Commits that touched at least one graph file; decides whether activity is used at all.
        int relevant = 0;
        for (CommitRecord commit : commits) {
            double ageDays = Math.max(0, nowSeconds - commit.getCommitTimeSeconds()) / SECONDS_PER_DAY;
            double weight = Math.pow(0.5, ageDays / halfLifeDays);
            Set<PackageNode> touchedPackages = new HashSet<>();
            for (String path : commit.getPaths()) {
                FileInfo file = byGitPath.get(path);
                if (file == null) continue;
                fileActivity.merge(file, weight, Double::sum);
                touchedPackages.add(graph.packageOf(file));
            }
            if (!touchedPackages.isEmpty()) {
                relevant++;
                touchedPackages.forEach(p -> packageActivity.merge(p, weight, Double::sum));
            }
        }
        return new ActivityScores(fileActivity, packageActivity, relevant);
    }

    /** True if at least one commit touched a file in the graph. */
    public boolean hasHistory() {
        return relevantCommits > 0;
    }

    public int getRelevantCommits() {
        return relevantCommits;
    }

    /** Decayed commit count of a file (0 if never changed in the analyzed window). */
    public double file(FileInfo file) {
        return fileActivity.getOrDefault(file, 0.0);
    }

    /** Decayed count of commits touching the package, each commit counted once. */
    public double pkg(PackageNode node) {
        return packageActivity.getOrDefault(node, 0.0);
    }
}
