package com.deepdoc.understand;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.graph.GraphFixture;
import com.deepdoc.ranker.ActivityScores;
import com.deepdoc.ranker.DocLevel;
import com.deepdoc.ranker.EntryPointDetector;
import com.deepdoc.ranker.ImportanceRanker;
import com.deepdoc.ranker.RankingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.deepdoc.testutil.ExampleRepos.write;
import static com.deepdoc.testutil.ExampleRepos.writeFourFile;
import static org.assertj.core.api.Assertions.assertThat;

class CodeUnderstandingServiceTest {

    @TempDir
    Path repo;

    @Test
    void outlinesTheFourFileShopAndLinksUsedTypes() throws IOException {
        writeFourFile(repo);
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        config.getUnderstand().setIncludeListed(true);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new CodeUnderstandingService(config, false)
                .understand(fx.graph, ranking, "Spring Boot Application");

        assertThat(result.getAstFiles()).isEqualTo(4);
        assertThat(result.getLlmMode()).isEqualTo("disabled");
        FileOutline service = result.getKnowledge().outlineOf(fx.file(
                "src/main/java/com/acme/shop/order/OrderService.java"));
        assertThat(service.getResponsibility()).isEqualTo("Creates an Order after validation.");
        assertThat(service.getResponsibilitySource()).isEqualTo("javadoc");
        assertThat(result.getKnowledge().uses(service.getFile()))
                .contains(fx.file("src/main/java/com/acme/shop/domain/Order.java"),
                        fx.file("src/main/java/com/acme/shop/order/OrderValidator.java"));

        FileOutline controller = result.getKnowledge().outlineOf(fx.file(
                "src/main/java/com/acme/shop/web/OrderController.java"));
        assertThat(controller.getResponsibility()).contains("HTTP");
        assertThat(controller.getSymbols().get(0).getAnnotations()).contains("RestController");
    }

    @Test
    void javascriptFileIsOutlinedLexically() throws IOException {
        write(repo.resolve("src/app.ts"),
                "export class App {",
                "  start() {}",
                "}",
                "export function main() { new App().start(); }");
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new CodeUnderstandingService(config, false)
                .understand(fx.graph, ranking, "Unknown");

        assertThat(result.getLexicalFiles()).isGreaterThanOrEqualTo(1);
        FileOutline app = result.getKnowledge().outlineOf(fx.file("src/app.ts"));
        assertThat(app.getEngine()).isEqualTo(FileOutline.ENGINE_LEXICAL);
        assertThat(app.typeNames()).contains("App");
        assertThat(app.methodNames()).contains("main");
        assertThat(app.getLevel()).isIn(DocLevel.DEEP, DocLevel.SUMMARY);
    }
}
