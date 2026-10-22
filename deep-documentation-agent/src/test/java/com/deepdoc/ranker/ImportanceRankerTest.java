package com.deepdoc.ranker;

import com.deepdoc.analyzer.RepositoryAnalyzer;
import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.DependencyGraphBuilder;
import com.deepdoc.model.FileInfo;
import com.deepdoc.repository.CommitRecord;
import com.deepdoc.testutil.ExampleRepos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Four-file example: commits OrderController 10, OrderService 20, OrderValidator 6, Order 3,
 * all made "now" so recency decay is 1.
 */
class ImportanceRankerTest {
    private static final String SRC = "src/main/java/com/acme/shop/";
    private static final long NOW = 1_800_000_000L;

    @TempDir
    Path repo;

    private AppConfig config;
    private DependencyGraph graph;

    @BeforeEach
    void setUp() throws IOException {
        ExampleRepos.writeFourFile(repo);
        config = ConfigLoader.load();
        graph = new DependencyGraphBuilder(config.getGraph())
                .build(new RepositoryAnalyzer(config).analyze(repo));
    }

    private FileInfo file(String name) {
        return graph.getFileGraph().vertexSet().stream()
                .filter(f -> f.getFilePath().getFileName().toString().equals(name))
                .findFirst().orElseThrow(AssertionError::new);
    }

    private ActivityScores activity() {
        List<CommitRecord> commits = new ArrayList<>();
        addCommits(commits, SRC + "web/OrderController.java", 10);
        addCommits(commits, SRC + "order/OrderService.java", 20);
        addCommits(commits, SRC + "order/OrderValidator.java", 6);
        addCommits(commits, SRC + "domain/Order.java", 3);
        return ActivityScores.fromCommits(graph, commits, repo.toAbsolutePath().normalize(), 180, NOW);
    }

    private static void addCommits(List<CommitRecord> commits, String path, int count) {
        for (int i = 0; i < count; i++) {
            commits.add(new CommitRecord(path + i, NOW, Collections.singletonList(path)));
        }
    }

    @Test
    void entryPointDetectionFindsController() {
        assertThat(EntryPointDetector.detect(graph)).containsExactly(file("OrderController.java"));
    }

    @Test
    void ranksWorkedExample() {
        config.getRanking().setDeepFraction(0.5);
        RankingResult result = new ImportanceRanker(config.getRanking())
                .rank(graph, activity(), EntryPointDetector.detect(graph));

        assertThat(result.usedActivity()).isTrue();
        assertThat(result.packageRank("order").getPageRank()).isCloseTo(0.473, within(0.001));
        assertThat(result.packageRank("domain").getPageRank()).isCloseTo(0.367, within(0.001));
        assertThat(result.packageRank("web").getPageRank()).isCloseTo(0.160, within(0.001));

        // structure percentile 1 / 0.5 / 0, activity percentile 1 / 0 / 0.5
        assertThat(result.getPackages()).extracting(PackageRank::getName).containsExactly("order", "domain", "web");
        assertThat(result.packageRank("order").getScore()).isCloseTo(1.00, within(1e-9));
        assertThat(result.packageRank("domain").getScore()).isCloseTo(0.30, within(1e-9));
        assertThat(result.packageRank("web").getScore()).isCloseTo(0.20, within(1e-9));
        assertThat(result.packageRank("domain").getRole()).isEqualTo(PackageRole.FOUNDATION);
        assertThat(result.packageRank("web").getRole()).isEqualTo(PackageRole.ORCHESTRATOR);
        assertThat(result.packageRank("order").getRole()).isEqualTo(PackageRole.INTERMEDIATE);

        FileRank service = result.rankOf(file("OrderService.java"));
        FileRank validator = result.rankOf(file("OrderValidator.java"));
        FileRank order = result.rankOf(file("Order.java"));
        FileRank controller = result.rankOf(file("OrderController.java"));
        assertThat(service.getPageRank()).isCloseTo(0.343, within(0.001));
        assertThat(order.getPageRank()).isCloseTo(0.337, within(0.001));
        assertThat(validator.getPageRank()).isCloseTo(0.204, within(0.001));
        assertThat(controller.getPageRank()).isCloseTo(0.116, within(0.001));

        assertThat(service.getPriority()).isCloseTo(1.00, within(1e-9));
        assertThat(validator.getPriority()).isCloseTo(0.489, within(0.001));
        assertThat(order.getPriority()).isCloseTo(0.30, within(1e-9));
        assertThat(controller.getPriority()).isCloseTo(0.20, within(1e-9));
        assertThat(validator.isBoundary()).isFalse();
        assertThat(controller.isEntryPoint()).isTrue();

        assertThat(result.getFiles()).containsExactly(service, validator, order, controller);
        assertThat(result.filesAt(DocLevel.DEEP)).containsExactly(service, validator);
        assertThat(result.filesAt(DocLevel.SUMMARY)).containsExactly(order, controller);
        assertThat(file("OrderService.java").getImportanceScore()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void fallsBackToStructureWhenThereIsNoHistory() {
        RankingResult result = new ImportanceRanker(config.getRanking())
                .rank(graph, ActivityScores.none(), EntryPointDetector.detect(graph));

        assertThat(result.usedActivity()).isFalse();
        assertThat(result.getPackages()).extracting(PackageRank::getScore).containsExactly(1.0, 0.5, 0.0);
        FileRank validator = result.rankOf(file("OrderValidator.java"));
        // (0.5 * 0.2039 / 0.3431 + 0.2 * 0) / 0.7
        assertThat(validator.getLocalScore()).isCloseTo(0.4245, within(0.001));
        assertThat(result.filesAt(DocLevel.DEEP)).hasSize(1);
        assertThat(result.getFiles().get(0).getFile()).isEqualTo(file("OrderService.java"));
    }

    @Test
    void decaysOldCommitsAndCountsEachPackageCommitOnce() {
        String service = SRC + "order/OrderService.java";
        String validator = SRC + "order/OrderValidator.java";
        long halfLifeAgo = NOW - 180L * 86_400;
        List<CommitRecord> commits = Arrays.asList(
                new CommitRecord("a", NOW, Arrays.asList(service, validator)),
                new CommitRecord("b", halfLifeAgo, Collections.singletonList(service)),
                new CommitRecord("c", NOW, Collections.singletonList("README.md")));

        ActivityScores scores = ActivityScores.fromCommits(graph, commits, repo.toAbsolutePath().normalize(), 180, NOW);

        assertThat(scores.getRelevantCommits()).isEqualTo(2);
        assertThat(scores.file(file("OrderService.java"))).isCloseTo(1.5, within(1e-9));
        assertThat(scores.file(file("OrderValidator.java"))).isCloseTo(1.0, within(1e-9));
        assertThat(scores.pkg(graph.getPackage("order"))).isCloseTo(1.5, within(1e-9));
    }

    @Test
    void percentilesShareTiedPositions() {
        List<String> nodes = Arrays.asList("a", "b", "c", "d");
        Map<String, Double> values = Map.of("a", 1.0, "b", 5.0, "c", 5.0, "d", 9.0);

        Map<String, Double> pct = ImportanceRanker.percentiles(nodes, values::get);

        assertThat(pct.get("a")).isEqualTo(0.0);
        assertThat(pct.get("b")).isEqualTo(0.5);
        assertThat(pct.get("c")).isEqualTo(0.5);
        assertThat(pct.get("d")).isEqualTo(1.0);
    }

    @Test
    void detectsCommonEntryPointShapes() {
        assertThat(EntryPointDetector.isEntryPoint("Java", "class A { public static void main(String[] a) {} }")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Java", "@SpringBootApplication class App {}")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("Java", "// @RestController\nclass NotOne {}")).isFalse();
        assertThat(EntryPointDetector.isEntryPoint("TypeScript", "app.listen(3000);")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("JavaScript", "router.get('/x', h);")).isTrue();
        assertThat(EntryPointDetector.isEntryPoint("JavaScript", "export const x = 1;")).isFalse();
    }
}
