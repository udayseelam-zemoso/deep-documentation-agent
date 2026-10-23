package com.deepdoc.graph;

import com.deepdoc.config.GraphSection;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.model.SourceReference;
import com.deepdoc.utils.LanguageDetector;
import com.deepdoc.utils.SourceFiles;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the file dependency graph and its package roll-up from a {@link RepositoryManifest}.
 *
 * <ol>
 *   <li>Select the files to rank: programming-language files (per {@code languages.yml}) that are
 *       not tests and that some extractor understands.</li>
 *   <li>Run one {@link DependencyExtractor} per {@link LanguageFamily} to get file-to-file edges.</li>
 *   <li>Group files into packages ({@link PackageGrouper}) and roll file edges up into
 *       package edges.</li>
 * </ol>
 */
public class DependencyGraphBuilder {
    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    private final GraphSection config;
    private final LanguageDetector languages = new LanguageDetector();

    public DependencyGraphBuilder(GraphSection config) {
        this.config = config;
    }

    public DependencyGraph build(RepositoryManifest manifest) {
        Path root = manifest.getRepositoryPath();
        Map<FileInfo, String> sources = new LinkedHashMap<>();
        Map<LanguageFamily, List<FileInfo>> filesByFamily = new EnumMap<>(LanguageFamily.class);
        Map<String, Integer> languageCounts = new TreeMap<>();
        int excludedTests = 0;
        int nonCode = 0;
        int unparsedCode = 0;

        List<FileInfo> files = new ArrayList<>(manifest.getFiles());
        files.sort(Comparator.comparing(f -> f.getFilePath().toString()));
        for (FileInfo file : files) {
            if (!languages.isProgrammingLanguage(file.getLanguage())) {
                nonCode++;
                continue;
            }
            LanguageFamily family = LanguageFamily.of(file.getLanguage());
            if (family == null) {
                unparsedCode++;
                continue;
            }
            if (!config.isIncludeTests() && TestFileDetector.isTest(root.relativize(file.getFilePath()))) {
                excludedTests++;
                continue;
            }
            String source = SourceFiles.read(file.getFilePath());
            if (source == null) {
                continue;
            }
            sources.put(file, source);
            filesByFamily.computeIfAbsent(family, k -> new ArrayList<>()).add(file);
            languageCounts.merge(file.getLanguage(), 1, Integer::sum);
        }

        SourceSet sourceSet = new SourceSet(root, sources);
        Map<FileInfo, DependencyExtractor> extractorOf = new HashMap<>();
        filesByFamily.forEach((family, members) -> {
            DependencyExtractor extractor = DependencyExtractors.create(family, sourceSet, members, config);
            members.forEach(f -> extractorOf.put(f, extractor));
        });

        Graph<FileInfo, DependencyEdge> fileGraph = new DefaultDirectedGraph<>(DependencyEdge.class);
        sources.keySet().forEach(fileGraph::addVertex);
        for (FileInfo file : sources.keySet()) {
            for (RawDependency dep : extractorOf.get(file).extract(file)) {
                DependencyEdge edge = fileGraph.getEdge(dep.from, dep.to);
                if (edge == null) {
                    edge = fileGraph.addEdge(dep.from, dep.to);
                }
                edge.addReferences(dep.referenceCount);
                edge.setEvidenceIfAbsent(SourceReference.line(dep.from.getFilePath(), dep.line));
            }
        }

        PackageGrouper.Result grouping = new PackageGrouper(config).group(packageKeys(root, fileGraph, extractorOf));

        Graph<PackageNode, PackageEdge> packageGraph = new DefaultDirectedGraph<>(PackageEdge.class);
        Map<String, PackageNode> nodes = new HashMap<>();
        Map<FileInfo, PackageNode> packageOf = new HashMap<>();
        grouping.getGroupOf().forEach((file, name) -> {
            PackageNode node = nodes.computeIfAbsent(name, n -> {
                PackageNode created = new PackageNode(n);
                packageGraph.addVertex(created);
                return created;
            });
            node.addFile(file);
            packageOf.put(file, node);
        });

        for (DependencyEdge edge : fileGraph.edgeSet()) {
            PackageNode from = packageOf.get(fileGraph.getEdgeSource(edge));
            PackageNode to = packageOf.get(fileGraph.getEdgeTarget(edge));
            if (from.equals(to)) {
                from.incrementInternalDependencies();
                continue;
            }
            PackageEdge packageEdge = packageGraph.getEdge(from, to);
            if (packageEdge == null) {
                packageEdge = packageGraph.addEdge(from, to);
            }
            packageEdge.add(edge);
        }

        logger.info("Dependency graph: {} files, {} dependencies, {} packages (depth {})",
                fileGraph.vertexSet().size(), fileGraph.edgeSet().size(),
                packageGraph.vertexSet().size(), grouping.getDepth());

        return new DependencyGraph(root, fileGraph, packageGraph, packageOf, grouping.getDepth(),
                grouping.getStrippedPrefixes(), excludedTests, nonCode, unparsedCode, languageCounts);
    }

    /** Declared namespace if the language has one (Java package, C# namespace), else the directory. */
    private static Map<FileInfo, PackageGrouper.Key> packageKeys(Path root, Graph<FileInfo, DependencyEdge> fileGraph,
                                                                 Map<FileInfo, DependencyExtractor> extractorOf) {
        Map<FileInfo, PackageGrouper.Key> keys = new LinkedHashMap<>();
        for (FileInfo file : fileGraph.vertexSet()) {
            String namespace = extractorOf.get(file).namespaceOf(file);
            if (namespace != null && !namespace.isEmpty()) {
                keys.put(file, new PackageGrouper.Key(PackageGrouper.Kind.NAMESPACE,
                        PackageGrouper.segments(namespace, "\\.")));
            } else {
                Path dir = root.relativize(file.getFilePath()).getParent();
                keys.put(file, new PackageGrouper.Key(PackageGrouper.Kind.DIRECTORY,
                        PackageGrouper.segments(dir == null ? "" : dir.toString(), "[/\\\\]")));
            }
        }
        return keys;
    }
}
