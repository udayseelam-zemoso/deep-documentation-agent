package com.deepdoc.ranker;

import com.deepdoc.config.RankingSection;
import com.deepdoc.graph.DependencyEdge;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.PackageEdge;
import com.deepdoc.graph.PackageNode;
import com.deepdoc.model.FileInfo;
import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Two-level importance ranking.
 *
 * <ol>
 *   <li>Packages: personalized PageRank on the package graph and recency-weighted activity,
 *       each turned into a percentile, then blended.</li>
 *   <li>Files: PageRank on the file graph, activity and a boundary flag, each normalized
 *       against the best file of the same package, then blended.</li>
 *   <li>Priority = package score x local score; the top share becomes {@link DocLevel#DEEP}.</li>
 * </ol>
 * Edge weights are {@code sqrt(count)} so one heavily used dependency cannot dominate, and every
 * edge gets a reverse edge of {@code backEdgeFactor} times its weight so high-level files that
 * use many others are not starved of rank. Entry points get a larger teleport prior.
 */
public class ImportanceRanker {
    private static final Logger logger = LoggerFactory.getLogger(ImportanceRanker.class);

    private final RankingSection config;

    public ImportanceRanker(RankingSection config) {
        this.config = config;
    }

    public RankingResult rank(DependencyGraph graph, ActivityScores activity, Set<FileInfo> entryPoints) {
        boolean useActivity = activity.hasHistory();
        List<PackageRank> packageRanks = rankPackages(graph, activity, entryPoints, useActivity);
        Map<PackageNode, PackageRank> byNode = new HashMap<>();
        packageRanks.forEach(p -> byNode.put(p.getNode(), p));

        Graph<FileInfo, DependencyEdge> fileGraph = graph.getFileGraph();
        List<FileInfo> files = new ArrayList<>(fileGraph.vertexSet());
        files.sort(Comparator.comparing(f -> f.getFilePath().toString()));
        Map<FileInfo, Map<FileInfo, Double>> fileEdges = new HashMap<>();
        for (DependencyEdge edge : fileGraph.edgeSet()) {
            addWithBackEdge(fileEdges, fileGraph.getEdgeSource(edge), fileGraph.getEdgeTarget(edge),
                    Math.sqrt(edge.getReferenceCount()));
        }
        Map<FileInfo, Double> filePrior = new HashMap<>();
        for (FileInfo file : files) {
            filePrior.put(file, entryPoints.contains(file) ? 1.0 + files.size() : 1.0);
        }
        Map<FileInfo, Double> filePageRank =
                WeightedPageRank.compute(files, fileEdges, filePrior, config.getDampingFactor());

        List<FileRank> fileRanks = new ArrayList<>();
        for (PackageNode node : graph.getPackages()) {
            List<FileInfo> members = node.getFiles();
            double maxPr = members.stream().mapToDouble(filePageRank::get).max().orElse(0);
            double maxActivity = members.stream().mapToDouble(f -> Math.log1p(activity.file(f))).max().orElse(0);
            for (FileInfo file : members) {
                boolean entry = entryPoints.contains(file);
                boolean boundary = entry || usedFromOtherPackage(graph, file);
                double structure = maxPr > 0 ? filePageRank.get(file) / maxPr : 0;
                double act = maxActivity > 0 ? Math.log1p(activity.file(file)) / maxActivity : 0;
                double local = useActivity
                        ? config.getLocalStructureWeight() * structure
                          + config.getLocalActivityWeight() * act
                          + config.getLocalBoundaryWeight() * (boundary ? 1 : 0)
                        : (config.getLocalStructureWeight() * structure
                           + config.getLocalBoundaryWeight() * (boundary ? 1 : 0))
                          / (config.getLocalStructureWeight() + config.getLocalBoundaryWeight());
                fileRanks.add(new FileRank(file, byNode.get(node), filePageRank.get(file),
                        activity.file(file), entry, boundary, local));
            }
        }

        fileRanks.sort(Comparator.comparingDouble(FileRank::getPriority).reversed()
                .thenComparing(Comparator.comparingDouble((FileRank f) -> f.getPackageRank().getScore()).reversed())
                .thenComparing(f -> f.getFile().getFilePath().toString()));
        assignLevels(fileRanks);
        fileRanks.forEach(f -> f.getFile().setImportanceScore(f.getPriority()));

        logger.info("Ranked {} packages and {} files ({} activity)", packageRanks.size(), fileRanks.size(),
                useActivity ? "with" : "without");
        return new RankingResult(packageRanks, fileRanks, useActivity);
    }

    private List<PackageRank> rankPackages(DependencyGraph graph, ActivityScores activity,
                                           Set<FileInfo> entryPoints, boolean useActivity) {
        Graph<PackageNode, PackageEdge> packageGraph = graph.getPackageGraph();
        List<PackageNode> packages = graph.getPackages();
        Map<PackageNode, Map<PackageNode, Double>> edges = new HashMap<>();
        for (PackageEdge edge : packageGraph.edgeSet()) {
            addWithBackEdge(edges, packageGraph.getEdgeSource(edge), packageGraph.getEdgeTarget(edge),
                    Math.sqrt(edge.getImportCount()));
        }
        Map<PackageNode, Double> prior = new HashMap<>();
        for (PackageNode node : packages) {
            boolean hasEntry = node.getFiles().stream().anyMatch(entryPoints::contains);
            prior.put(node, hasEntry ? 1.0 + packages.size() : 1.0);
        }
        Map<PackageNode, Double> pageRank =
                WeightedPageRank.compute(packages, edges, prior, config.getDampingFactor());

        Map<PackageNode, Double> structurePct = percentiles(packages, pageRank::get);
        Map<PackageNode, Double> activityPct = percentiles(packages, p -> Math.log1p(activity.pkg(p)));

        List<PackageRank> ranks = new ArrayList<>();
        for (PackageNode node : packages) {
            double structure = structurePct.get(node);
            double act = useActivity ? activityPct.get(node) : 0;
            double score = useActivity
                    ? (config.getStructureWeight() * structure + config.getActivityWeight() * act)
                      / (config.getStructureWeight() + config.getActivityWeight())
                    : structure;
            ranks.add(new PackageRank(node, pageRank.get(node), activity.pkg(node), structure, act, score,
                    packageGraph.inDegreeOf(node), packageGraph.outDegreeOf(node)));
        }
        ranks.sort(Comparator.comparingDouble(PackageRank::getScore).reversed()
                .thenComparing(Comparator.comparingDouble(PackageRank::getPageRank).reversed())
                .thenComparing(PackageRank::getName));
        return ranks;
    }

    /**
     * DEEP: the top {@code deepFraction} of files by priority (at least one).
     * SUMMARY: the best file of every package that has no DEEP file, and every entry point not already DEEP.
     * LISTED: everything else.
     */
    private void assignLevels(List<FileRank> ranked) {
        if (ranked.isEmpty()) {
            return;
        }
        int deepCount = Math.max(1, (int) Math.round(config.getDeepFraction() * ranked.size()));
        Set<String> packagesWithSummary = new HashSet<>();
        Map<String, FileRank> bestInPackage = new LinkedHashMap<>();
        for (FileRank f : ranked) {
            bestInPackage.merge(f.getPackageRank().getName(), f,
                    (a, b) -> b.getLocalScore() > a.getLocalScore() ? b : a);
        }
        for (int i = 0; i < ranked.size(); i++) {
            FileRank f = ranked.get(i);
            if (i < deepCount) {
                f.setLevel(DocLevel.DEEP);
                packagesWithSummary.add(f.getPackageRank().getName());
            }
        }
        for (FileRank f : ranked) {
            if (f.getLevel() == DocLevel.DEEP) continue;
            boolean bestOfPackage = bestInPackage.get(f.getPackageRank().getName()) == f
                    && !packagesWithSummary.contains(f.getPackageRank().getName());
            if (bestOfPackage || f.isEntryPoint()) {
                f.setLevel(DocLevel.SUMMARY);
            }
        }
    }

    private boolean usedFromOtherPackage(DependencyGraph graph, FileInfo file) {
        Graph<FileInfo, DependencyEdge> fileGraph = graph.getFileGraph();
        PackageNode own = graph.packageOf(file);
        for (DependencyEdge edge : fileGraph.incomingEdgesOf(file)) {
            if (!graph.packageOf(fileGraph.getEdgeSource(edge)).equals(own)) {
                return true;
            }
        }
        return false;
    }

    private <V> void addWithBackEdge(Map<V, Map<V, Double>> edges, V from, V to, double weight) {
        edges.computeIfAbsent(from, k -> new HashMap<>()).merge(to, weight, Double::sum);
        if (config.getBackEdgeFactor() > 0) {
            edges.computeIfAbsent(to, k -> new HashMap<>()).merge(from, config.getBackEdgeFactor() * weight, Double::sum);
        }
    }

    /**
     * Percentile rank in [0, 1]: lowest value 0, highest 1, ties share their average position.
     * A single node gets 1.
     */
    static <V> Map<V, Double> percentiles(List<V> nodes, ToDoubleFunction<V> value) {
        Map<V, Double> result = new HashMap<>();
        int n = nodes.size();
        if (n == 1) {
            result.put(nodes.get(0), 1.0);
            return result;
        }
        List<V> sorted = new ArrayList<>(nodes);
        sorted.sort(Comparator.comparingDouble(value));
        int i = 0;
        while (i < n) {
            int j = i;
            double v = value.applyAsDouble(sorted.get(i));
            while (j + 1 < n && Math.abs(value.applyAsDouble(sorted.get(j + 1)) - v) < 1e-12) j++;
            double pct = ((i + j) / 2.0) / (n - 1);
            for (int k = i; k <= j; k++) result.put(sorted.get(k), pct);
            i = j + 1;
        }
        return result;
    }
}
