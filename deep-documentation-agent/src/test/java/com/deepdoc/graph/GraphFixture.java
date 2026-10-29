package com.deepdoc.graph;

import com.deepdoc.analyzer.RepositoryAnalyzer;
import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.model.FileInfo;
import com.deepdoc.repository.CommitRecord;
import org.jgrapht.Graph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Builds a graph for a temp repository and looks up files and edges by repository-relative path. */
public final class GraphFixture {
    public final DependencyGraph graph;

    private GraphFixture(DependencyGraph graph) {
        this.graph = graph;
    }

    public static GraphFixture build(Path repo) throws IOException {
        return build(repo, ConfigLoader.load());
    }

    static GraphFixture build(Path repo, AppConfig config) throws IOException {
        return build(repo, config, Collections.emptyList());
    }

    /** Builds with the given history; commit paths are relative to {@code repo}. */
    static GraphFixture build(Path repo, AppConfig config, List<CommitRecord> commits) throws IOException {
        return new GraphFixture(new DependencyGraphBuilder(config.getGraph())
                .build(new RepositoryAnalyzer(config).analyze(repo), commits, repo));
    }

    public FileInfo file(String relativePath) {
        return graph.getFileGraph().vertexSet().stream()
                .filter(f -> graph.relativePath(f).equals(relativePath))
                .findFirst()
                .orElseThrow(() -> new AssertionError("not in graph: " + relativePath));
    }

    DependencyEdge edge(String from, String to) {
        return graph.getFileGraph().getEdge(file(from), file(to));
    }

    /** Relative paths of everything {@code from} depends on, sorted. */
    Set<String> targetsOf(String from) {
        Graph<FileInfo, DependencyEdge> g = graph.getFileGraph();
        Set<String> targets = new TreeSet<>();
        for (DependencyEdge e : g.outgoingEdgesOf(file(from))) {
            targets.add(graph.relativePath(g.getEdgeTarget(e)));
        }
        return targets;
    }
}
