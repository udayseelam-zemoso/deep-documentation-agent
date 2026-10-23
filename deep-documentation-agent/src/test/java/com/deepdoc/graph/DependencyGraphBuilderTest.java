package com.deepdoc.graph;

import com.deepdoc.analyzer.RepositoryAnalyzer;
import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.testutil.ExampleRepos;
import org.jgrapht.Graph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DependencyGraphBuilderTest {

    @TempDir
    Path repo;

    private DependencyGraph build(AppConfig config) throws IOException {
        RepositoryManifest manifest = new RepositoryAnalyzer(config).analyze(repo);
        return new DependencyGraphBuilder(config.getGraph()).build(manifest);
    }

    private static FileInfo file(DependencyGraph graph, String fileName) {
        return graph.getFileGraph().vertexSet().stream()
                .filter(f -> f.getFilePath().getFileName().toString().equals(fileName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing " + fileName));
    }

    @Test
    void buildsFileGraphAndPackageRollupForShopExample() throws IOException {
        ExampleRepos.writeShop(repo);

        DependencyGraph graph = build(ConfigLoader.load());

        assertThat(graph.getFileGraph().vertexSet()).hasSize(10);
        assertThat(graph.getFileGraph().edgeSet()).hasSize(18);
        assertThat(graph.getExcludedTestFiles()).isEqualTo(1);

        assertThat(graph.getStrippedPrefixes()).containsExactly("com.acme.shop");
        assertThat(graph.getGroupingDepth()).isEqualTo(1);
        assertThat(graph.getPackages()).extracting(PackageNode::getName)
                .containsExactly("domain", "order", "payment", "persistence", "util", "web");

        Graph<PackageNode, PackageEdge> packages = graph.getPackageGraph();
        assertThat(packages.edgeSet()).hasSize(12);
        PackageNode order = graph.getPackage("order");
        assertThat(order.getFiles()).hasSize(2);
        assertThat(order.getInternalDependencies()).isEqualTo(1);
        PackageEdge orderToDomain = packages.getEdge(order, graph.getPackage("domain"));
        assertThat(orderToDomain.getImportCount()).isEqualTo(2);
        assertThat(orderToDomain.getEvidence()).isNotEmpty();
        assertThat(packages.getEdge(graph.getPackage("persistence"), graph.getPackage("domain")).getImportCount())
                .isEqualTo(2);
        assertThat(packages.getEdge(graph.getPackage("util"), graph.getPackage("domain"))).isNull();
    }

    @Test
    void countsReferencesAndDetectsSamePackageUsageWithoutImport() throws IOException {
        ExampleRepos.writeFourFile(repo);

        DependencyGraph graph = build(ConfigLoader.load());
        Graph<FileInfo, DependencyEdge> files = graph.getFileGraph();

        FileInfo service = file(graph, "OrderService.java");
        FileInfo validator = file(graph, "OrderValidator.java");
        FileInfo order = file(graph, "Order.java");

        assertThat(files.edgeSet()).hasSize(4);
        // "Order" in the Javadoc comment and the string literal must not count.
        assertThat(files.getEdge(service, order).getReferenceCount()).isEqualTo(4);
        assertThat(files.getEdge(service, validator).getReferenceCount()).isEqualTo(1);
        assertThat(files.getEdge(service, order).getEvidence().getStartLine()).isEqualTo(3);
        assertThat(files.outgoingEdgesOf(order)).isEmpty();

        assertThat(graph.getPackages()).extracting(PackageNode::getName)
                .containsExactly("domain", "order", "web");
        assertThat(graph.getPackage("order").getInternalDependencies()).isEqualTo(1);
    }

    @Test
    void resolvesRelativeJsAndTsImports() throws IOException {
        ExampleRepos.write(repo.resolve("src/app.ts"),
                "import { Client } from './api/client';",
                "import type {",
                "  User,",
                "} from './models/user';",
                "import './styles/setup';",
                "const util = require('../src/lib/util.js');",
                "const lazy = () => import('./pages/home');",
                "import React from 'react';");
        ExampleRepos.write(repo.resolve("src/api/client.ts"), "export * from '../models/user.js';");
        ExampleRepos.write(repo.resolve("src/models/user.ts"), "export interface User {}");
        ExampleRepos.write(repo.resolve("src/styles/setup.js"), "");
        ExampleRepos.write(repo.resolve("src/lib/util.js"), "module.exports = {};");
        ExampleRepos.write(repo.resolve("src/pages/home/index.tsx"), "export default () => null;");
        ExampleRepos.write(repo.resolve("src/pages/home/home.test.tsx"), "import Home from './index';");

        DependencyGraph graph = build(ConfigLoader.load());
        Graph<FileInfo, DependencyEdge> files = graph.getFileGraph();
        FileInfo app = file(graph, "app.ts");

        assertThat(files.outgoingEdgesOf(app)).extracting(e -> files.getEdgeTarget(e).getFilePath().getFileName().toString())
                .containsExactlyInAnyOrder("client.ts", "user.ts", "setup.js", "util.js", "index.tsx");
        assertThat(files.getEdge(file(graph, "client.ts"), file(graph, "user.ts"))).isNotNull();
        assertThat(graph.getExcludedTestFiles()).isEqualTo(1);
        assertThat(graph.getStrippedPrefixes()).containsExactly("src");
        assertThat(graph.getPackages()).extracting(PackageNode::getName)
                .contains("(root)/", "api/", "models/", "pages/");
    }
}
