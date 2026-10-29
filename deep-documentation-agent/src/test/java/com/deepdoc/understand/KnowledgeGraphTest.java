package com.deepdoc.understand;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.graph.GraphFixture;
import com.deepdoc.ranker.ActivityScores;
import com.deepdoc.ranker.EntryPointDetector;
import com.deepdoc.ranker.ImportanceRanker;
import com.deepdoc.ranker.RankingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.deepdoc.testutil.ExampleRepos.writeFourFile;
import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeGraphTest {

    @TempDir
    Path repo;

    @Test
    void fourFileShopLinksServiceToOrderAndValidator() throws IOException {
        writeFourFile(repo);
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        config.getUnderstand().setIncludeListed(true);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new JavaUnderstandingService(config).understand(ranking);
        KnowledgeGraph graph = result.getKnowledge();

        FileOutline service = graph.outlineOf(fx.file(
                "src/main/java/com/acme/shop/order/OrderService.java"));
        assertThat(service).isNotNull();
        assertThat(service.primaryJavadoc()).isEqualTo("Creates an Order after validation.");
        assertThat(graph.uses(service.getFile()))
                .contains(fx.file("src/main/java/com/acme/shop/domain/Order.java"),
                        fx.file("src/main/java/com/acme/shop/order/OrderValidator.java"));
        assertThat(graph.uniqueTypes()).containsKeys("OrderService", "Order", "OrderValidator", "OrderController");
        assertThat(graph.getFacts()).isNotEmpty();
    }

    @Test
    void ambiguousTypeNameDoesNotBecomeAUseEdge() {
        // Two files declare Helper; a third mentions Helper. No unique owner, so no edge.
        FileOutline a = typeOutline("a/Helper.java", "Helper");
        FileOutline b = typeOutline("b/Helper.java", "Helper");
        FileOutline c = mentionOutline("c/User.java", "User", "Helper");

        KnowledgeGraph graph = KnowledgeGraph.from(java.util.Arrays.asList(a, b, c));

        assertThat(graph.uniqueTypes()).doesNotContainKey("Helper");
        assertThat(graph.uses(c.getFile())).isEmpty();
    }

    private static FileOutline typeOutline(String path, String typeName) {
        com.deepdoc.model.FileInfo file = new com.deepdoc.model.FileInfo(
                java.nio.file.Paths.get(path), "Java", 4, java.time.LocalDateTime.now());
        DeclaredSymbol type = new DeclaredSymbol(typeName, SymbolKind.CLASS, 1, "public", "",
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.emptyList());
        return new FileOutline(file, com.deepdoc.ranker.DocLevel.DEEP, FileOutline.ENGINE_JAVAPARSER,
                true, "", java.util.Collections.emptyList(), java.util.Collections.singletonList(type));
    }

    private static FileOutline mentionOutline(String path, String typeName, String mentioned) {
        com.deepdoc.model.FileInfo file = new com.deepdoc.model.FileInfo(
                java.nio.file.Paths.get(path), "Java", 4, java.time.LocalDateTime.now());
        DeclaredSymbol type = new DeclaredSymbol(typeName, SymbolKind.CLASS, 1, "public", "",
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.emptyList(), java.util.Collections.emptyList(),
                java.util.Collections.singletonList(mentioned));
        return new FileOutline(file, com.deepdoc.ranker.DocLevel.DEEP, FileOutline.ENGINE_JAVAPARSER,
                true, "", java.util.Collections.emptyList(), java.util.Collections.singletonList(type));
    }
}
