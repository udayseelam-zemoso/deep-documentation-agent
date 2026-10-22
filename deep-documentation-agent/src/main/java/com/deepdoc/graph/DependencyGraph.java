package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;
import org.jgrapht.Graph;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The two views used for ranking: a file graph (edges = static dependencies) and the
 * package graph obtained by rolling file edges up to package level.
 */
public final class DependencyGraph {
    private final Path repositoryRoot;
    private final Graph<FileInfo, DependencyEdge> fileGraph;
    private final Graph<PackageNode, PackageEdge> packageGraph;
    private final Map<FileInfo, PackageNode> packageOf;
    private final int groupingDepth;
    private final List<String> strippedPrefixes;
    private final int excludedTestFiles;
    private final int unsupportedFiles;

    DependencyGraph(Path repositoryRoot,
                    Graph<FileInfo, DependencyEdge> fileGraph,
                    Graph<PackageNode, PackageEdge> packageGraph,
                    Map<FileInfo, PackageNode> packageOf,
                    int groupingDepth,
                    List<String> strippedPrefixes,
                    int excludedTestFiles,
                    int unsupportedFiles) {
        this.repositoryRoot = repositoryRoot;
        this.fileGraph = fileGraph;
        this.packageGraph = packageGraph;
        this.packageOf = packageOf;
        this.groupingDepth = groupingDepth;
        this.strippedPrefixes = strippedPrefixes;
        this.excludedTestFiles = excludedTestFiles;
        this.unsupportedFiles = unsupportedFiles;
    }

    public Path getRepositoryRoot() {
        return repositoryRoot;
    }

    public Graph<FileInfo, DependencyEdge> getFileGraph() {
        return fileGraph;
    }

    public Graph<PackageNode, PackageEdge> getPackageGraph() {
        return packageGraph;
    }

    public PackageNode packageOf(FileInfo file) {
        return packageOf.get(file);
    }

    public PackageNode getPackage(String name) {
        for (PackageNode node : packageGraph.vertexSet()) {
            if (node.getName().equals(name)) {
                return node;
            }
        }
        return null;
    }

    /** Packages sorted by name. */
    public List<PackageNode> getPackages() {
        List<PackageNode> packages = new ArrayList<>(packageGraph.vertexSet());
        packages.sort(Comparator.comparing(PackageNode::getName));
        return packages;
    }

    public int getGroupingDepth() {
        return groupingDepth;
    }

    public List<String> getStrippedPrefixes() {
        return Collections.unmodifiableList(strippedPrefixes);
    }

    public int getExcludedTestFiles() {
        return excludedTestFiles;
    }

    /** Discovered files in languages without a dependency extractor (not part of the graph). */
    public int getUnsupportedFiles() {
        return unsupportedFiles;
    }

    public String relativePath(FileInfo file) {
        return repositoryRoot.relativize(file.getFilePath()).toString().replace('\\', '/');
    }
}
