package com.deepdoc.graph;

import com.deepdoc.config.GraphSection;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.model.SourceReference;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.deepdoc.utils.SourceFiles;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the file dependency graph and its package roll-up from a {@link RepositoryManifest}.
 */
public class DependencyGraphBuilder {
    private static final Logger logger = LoggerFactory.getLogger(DependencyGraphBuilder.class);

    static final String JAVA = "Java";
    static final Set<String> JS_LANGUAGES = new HashSet<>(Arrays.asList("JavaScript", "TypeScript"));

    private final GraphSection config;

    public DependencyGraphBuilder(GraphSection config) {
        this.config = config;
    }

    public DependencyGraph build(RepositoryManifest manifest) {
        Path root = manifest.getRepositoryPath();
        Map<FileInfo, String> javaSources = new LinkedHashMap<>();
        Map<FileInfo, String> jsSources = new LinkedHashMap<>();
        int excludedTests = 0;
        int unsupported = 0;

        List<FileInfo> files = new ArrayList<>(manifest.getFiles());
        files.sort(Comparator.comparing(f -> f.getFilePath().toString()));
        for (FileInfo file : files) {
            boolean isJava = JAVA.equals(file.getLanguage());
            boolean isJs = JS_LANGUAGES.contains(file.getLanguage());
            if (!isJava && !isJs) {
                unsupported++;
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
            (isJava ? javaSources : jsSources).put(file, source);
        }

        JavaDependencyExtractor javaExtractor = new JavaDependencyExtractor(javaSources);
        Map<Path, FileInfo> jsByPath = new HashMap<>();
        jsSources.keySet().forEach(f -> jsByPath.put(f.getFilePath().toAbsolutePath().normalize(), f));
        JsDependencyExtractor jsExtractor = new JsDependencyExtractor(jsByPath);

        Graph<FileInfo, DependencyEdge> fileGraph = new DefaultDirectedGraph<>(DependencyEdge.class);
        javaSources.keySet().forEach(fileGraph::addVertex);
        jsSources.keySet().forEach(fileGraph::addVertex);

        List<RawDependency> dependencies = new ArrayList<>();
        javaSources.keySet().forEach(f -> dependencies.addAll(javaExtractor.extract(f)));
        jsSources.forEach((f, src) -> dependencies.addAll(jsExtractor.extract(f, src)));
        for (RawDependency dep : dependencies) {
            DependencyEdge edge = fileGraph.getEdge(dep.from, dep.to);
            if (edge == null) {
                edge = fileGraph.addEdge(dep.from, dep.to);
            }
            edge.addReferences(dep.referenceCount);
            edge.setEvidenceIfAbsent(SourceReference.line(dep.from.getFilePath(), dep.line));
        }

        Map<FileInfo, PackageGrouper.Key> keys = new LinkedHashMap<>();
        for (FileInfo file : fileGraph.vertexSet()) {
            String pkg = javaSources.containsKey(file) ? javaExtractor.packageName(file) : "";
            if (!pkg.isEmpty()) {
                keys.put(file, new PackageGrouper.Key(PackageGrouper.Kind.JAVA_PACKAGE,
                        PackageGrouper.segments(pkg, "\\.")));
            } else {
                Path dir = root.relativize(file.getFilePath()).getParent();
                keys.put(file, new PackageGrouper.Key(PackageGrouper.Kind.DIRECTORY,
                        PackageGrouper.segments(dir == null ? "" : dir.toString(), "[/\\\\]")));
            }
        }
        PackageGrouper.Result grouping = new PackageGrouper(config).group(keys);

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
                grouping.getStrippedPrefixes(), excludedTests, unsupported);
    }
}
