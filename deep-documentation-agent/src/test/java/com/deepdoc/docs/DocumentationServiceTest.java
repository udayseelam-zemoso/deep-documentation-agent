package com.deepdoc.docs;

import com.deepdoc.analyzer.RepositoryAnalyzer;
import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.GraphFixture;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.ranker.ActivityScores;
import com.deepdoc.ranker.EntryPointDetector;
import com.deepdoc.ranker.ImportanceRanker;
import com.deepdoc.ranker.RankingResult;
import com.deepdoc.repository.CommitRecord;
import com.deepdoc.understand.CodeUnderstandingService;
import com.deepdoc.understand.UnderstandingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.deepdoc.testutil.ExampleRepos.write;
import static com.deepdoc.testutil.ExampleRepos.writeFourFile;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentationServiceTest {

    @TempDir
    Path repo;

    @TempDir
    Path output;

    @Test
    void writesOverviewArchitectureAndPackagePagesForTheFourFileShop() throws IOException {
        writeFourFile(repo);
        AppConfig config = ConfigLoader.load();
        config.getRanking().setDeepFraction(0.5);
        config.getUnderstand().setIncludeListed(true);

        GraphFixture fx = GraphFixture.build(repo, config);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, fourFileActivity(fx.graph), EntryPointDetector.detect(fx.graph));
        UnderstandingResult understanding = new CodeUnderstandingService(config, false)
                .understand(fx.graph, ranking, "Unknown");
        RepositoryManifest manifest = new RepositoryAnalyzer(config).analyze(repo);

        DocumentationResult result = new DocumentationService(config)
                .generate(output, manifest, fx.graph, ranking, understanding);

        assertThat(result.getWrittenFiles()).hasSize(5);
        assertThat(result.getPackagePages()).isEqualTo(3);
        assertThat(result.getDeepSections()).isEqualTo(2);
        assertThat(result.getSummarySections()).isEqualTo(2);
        assertThat(result.hasDiagrams()).isTrue();

        String readme = read(output.resolve("README.md"));
        assertThat(readme).contains("```mermaid");
        assertThat(readme).contains("flowchart LR");
        assertThat(readme).contains("[order](packages/order.md)");
        assertThat(readme).contains("[domain](packages/domain.md)");
        assertThat(readme).contains("[web](packages/web.md)");
        assertThat(readme).contains("2 deep, 2 summary");

        String architecture = read(output.resolve("architecture.md"));
        assertThat(architecture).contains("Package graph");
        assertThat(architecture).contains("`web` → `order`");
        assertThat(architecture).contains("OrderController.java:");

        String order = read(output.resolve("packages/order.md"));
        assertThat(order).contains("Package `order`");
        assertThat(order).contains("`OrderService.java` (DEEP)");
        assertThat(order).contains("Creates an Order after validation.");
        assertThat(order).contains("JavaParser (AST)");
        assertThat(order).contains("`create`");
        assertThat(order).contains("OrderService.java:");
        assertThat(order).contains("#L");
        assertThat(order).contains("`OrderValidator.java` (DEEP)");

        String domain = read(output.resolve("packages/domain.md"));
        assertThat(domain).contains("(SUMMARY)");
        assertThat(domain).contains("Order.java");
        assertThat(domain).contains("FOUNDATION");

        String web = read(output.resolve("packages/web.md"));
        assertThat(web).contains("ORCHESTRATOR");
        assertThat(web).contains("entry point");
        assertThat(web).contains("Handles HTTP requests for order.");
    }

    @Test
    void listedFilesAppearAsATableWhenTheyWereNotOutlined() throws IOException {
        writeFourFile(repo);
        AppConfig config = ConfigLoader.load();
        // Default deepFraction 0.2 → one DEEP file; OrderValidator stays LISTED and is skipped
        // by understanding (includeListed is false).

        GraphFixture fx = GraphFixture.build(repo, config);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));
        UnderstandingResult understanding = new CodeUnderstandingService(config, false)
                .understand(fx.graph, ranking, "Unknown");
        RepositoryManifest manifest = new RepositoryAnalyzer(config).analyze(repo);

        new DocumentationService(config).generate(output, manifest, fx.graph, ranking, understanding);

        String order = read(output.resolve("packages/order.md"));
        assertThat(order).contains("Other files");
        assertThat(order).contains("OrderValidator.java");
        assertThat(order).contains("Order validator.");
    }

    @Test
    void diagramsCanBeTurnedOff() throws IOException {
        writeFourFile(repo);
        AppConfig config = ConfigLoader.load();
        config.getOutput().setGenerateDiagrams(false);
        GraphFixture fx = GraphFixture.build(repo, config);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));
        UnderstandingResult understanding = new CodeUnderstandingService(config, false)
                .understand(fx.graph, ranking, "Unknown");
        RepositoryManifest manifest = new RepositoryAnalyzer(config).analyze(repo);

        DocumentationResult result = new DocumentationService(config)
                .generate(output, manifest, fx.graph, ranking, understanding);

        assertThat(result.hasDiagrams()).isFalse();
        assertThat(read(output.resolve("README.md"))).doesNotContain("```mermaid");
        assertThat(read(output.resolve("architecture.md"))).doesNotContain("```mermaid");
    }

    @Test
    void lexicalJavascriptOutlineIsCitedOnThePackagePage() throws IOException {
        write(repo.resolve("src/app.ts"),
                "export class App {",
                "  start() {}",
                "}",
                "export function main() { new App().start(); }");
        AppConfig config = ConfigLoader.load();
        GraphFixture fx = GraphFixture.build(repo, config);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));
        UnderstandingResult understanding = new CodeUnderstandingService(config, false)
                .understand(fx.graph, ranking, "Unknown");
        RepositoryManifest manifest = new RepositoryAnalyzer(config).analyze(repo);

        new DocumentationService(config).generate(output, manifest, fx.graph, ranking, understanding);

        Path packagesDir = output.resolve("packages");
        Path packagePage;
        try (java.util.stream.Stream<Path> stream = Files.list(packagesDir)) {
            packagePage = stream.filter(p -> p.toString().endsWith(".md"))
                    .findFirst()
                    .orElseThrow();
        }
        String body = read(packagePage);
        assertThat(body).contains("app.ts");
        assertThat(body).contains("lexical scan");
        assertThat(body).contains("`App`");
    }

    @Test
    void resolveOutputDirectoryPrefersTheFlagThenTheDefaultFolder() {
        AppConfig config = ConfigLoader.load();
        Path repoRoot = Path.of("/tmp/shop").toAbsolutePath();

        assertThat(DocumentationService.resolveOutputDirectory(repoRoot, null, config.getOutput()))
                .isEqualTo(repoRoot.resolve("docs"));
        assertThat(DocumentationService.resolveOutputDirectory(repoRoot, "  ", config.getOutput()))
                .isEqualTo(repoRoot.resolve("docs"));
        assertThat(DocumentationService.resolveOutputDirectory(repoRoot, "/var/out", config.getOutput()))
                .isEqualTo(Path.of("/var/out"));
    }

    /**
     * Same commit weights as {@code ImportanceRankerTest}: Controller 10, Service 20,
     * Validator 6, Order 3. That is the worked example in {@code design/README.md}.
     */
    private ActivityScores fourFileActivity(DependencyGraph graph) {
        String src = "src/main/java/com/acme/shop/";
        long now = 1_800_000_000L;
        List<CommitRecord> commits = new ArrayList<>();
        addCommits(commits, src + "web/OrderController.java", 10, now);
        addCommits(commits, src + "order/OrderService.java", 20, now);
        addCommits(commits, src + "order/OrderValidator.java", 6, now);
        addCommits(commits, src + "domain/Order.java", 3, now);
        return ActivityScores.fromCommits(graph, commits, repo.toAbsolutePath().normalize(), 180, now);
    }

    private static void addCommits(List<CommitRecord> commits, String path, int count, long now) {
        for (int i = 0; i < count; i++) {
            commits.add(new CommitRecord(path + i, now, Collections.singletonList(path)));
        }
    }

    private static String read(Path file) throws IOException {
        assertThat(file).exists();
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
