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
 * Recency-weighted commit counts. A commit of age {@code t} days counts {@code 0.5^(t / halfLife)}.
 * A package counts each commit once, however many of its files the commit touched.
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

    public static ActivityScores none() {
        return new ActivityScores(new HashMap<>(), new HashMap<>(), 0);
    }

    /**
     * @param workTree Git work tree the commit paths are relative to; may be a parent of the
     *                 analyzed directory
     */
    public static ActivityScores fromCommits(DependencyGraph graph, List<CommitRecord> commits, Path workTree,
                                             double halfLifeDays, long nowSeconds) {
        Map<String, FileInfo> byGitPath = new HashMap<>();
        for (FileInfo file : graph.getFileGraph().vertexSet()) {
            Path absolute = file.getFilePath().toAbsolutePath().normalize();
            if (absolute.startsWith(workTree)) {
                byGitPath.put(workTree.relativize(absolute).toString().replace('\\', '/'), file);
            }
        }

        Map<FileInfo, Double> fileActivity = new HashMap<>();
        Map<PackageNode, Double> packageActivity = new HashMap<>();
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

    public boolean hasHistory() {
        return relevantCommits > 0;
    }

    public int getRelevantCommits() {
        return relevantCommits;
    }

    public double file(FileInfo file) {
        return fileActivity.getOrDefault(file, 0.0);
    }

    public double pkg(PackageNode node) {
        return packageActivity.getOrDefault(node, 0.0);
    }
}
