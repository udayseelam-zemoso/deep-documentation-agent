package com.deepdoc.understand;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.graph.GraphFixture;
import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.ActivityScores;
import com.deepdoc.ranker.EntryPointDetector;
import com.deepdoc.ranker.ImportanceRanker;
import com.deepdoc.ranker.RankingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.deepdoc.testutil.ExampleRepos.write;
import static com.deepdoc.testutil.ExampleRepos.writeFourFile;
import static org.assertj.core.api.Assertions.assertThat;

class JavaUnderstandingServiceTest {

    @TempDir
    Path repo;

    @Test
    void defaultConfigOutlinesDeepAndSummaryAndSkipsListed() throws IOException {
        writeFourFile(repo);
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new JavaUnderstandingService(config).understand(ranking);

        FileInfo service = fx.file("src/main/java/com/acme/shop/order/OrderService.java");
        FileInfo order = fx.file("src/main/java/com/acme/shop/domain/Order.java");
        FileInfo controller = fx.file("src/main/java/com/acme/shop/web/OrderController.java");
        FileInfo validator = fx.file("src/main/java/com/acme/shop/order/OrderValidator.java");

        assertThat(filesOf(result.deepOutlines())).containsExactly(service);
        assertThat(filesOf(result.summaryOutlines())).containsExactly(order, controller);
        assertThat(result.listedOutlines()).isEmpty();
        assertThat(result.getSkippedListed()).isEqualTo(1);
        assertThat(result.getAstFiles()).isEqualTo(3);
        assertThat(result.getKnowledge().outlineOf(validator)).isNull();

        assertThat(result.getKnowledge().factsOf(service))
                .anyMatch(f -> f.getKind() == FactKind.DECLARES_METHOD && f.getObject().equals("create"));
        assertThat(result.getKnowledge().factsOf(order))
                .noneMatch(f -> f.getKind() == FactKind.DECLARES_METHOD);
        assertThat(result.getKnowledge().factsOf(controller))
                .noneMatch(f -> f.getKind() == FactKind.DECLARES_METHOD);
        assertThat(result.getKnowledge().factsOf(controller))
                .anyMatch(f -> f.getKind() == FactKind.ANNOTATED && f.getObject().equals("RestController"));

        assertThat(result.getLlmMode()).isEqualTo("heuristic");
        assertThat(result.getKnowledge().outlineOf(service).getResponsibility())
                .isEqualTo("Creates an Order after validation.");
        assertThat(result.getKnowledge().outlineOf(service).getResponsibilitySource()).isEqualTo("javadoc");
        assertThat(result.getKnowledge().outlineOf(order).getResponsibility()).isEqualTo("Order class.");
        assertThat(result.getKnowledge().outlineOf(controller).getResponsibility())
                .isEqualTo("Handles HTTP requests for order.");
        assertThat(result.getKnowledge().outlineOf(controller).getResponsibilitySource()).isEqualTo("heuristic");
    }

    @Test
    void includeListedOutlinesTheFourFileJavaShop() throws IOException {
        writeFourFile(repo);
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        config.getUnderstand().setIncludeListed(true);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new JavaUnderstandingService(config).understand(ranking);

        FileInfo validator = fx.file("src/main/java/com/acme/shop/order/OrderValidator.java");
        assertThat(result.getAstFiles()).isEqualTo(4);
        assertThat(result.getLexicalFiles()).isZero();
        assertThat(result.getSkippedListed()).isZero();
        assertThat(filesOf(result.listedOutlines())).containsExactly(validator);
        assertThat(result.getKnowledge().factsOf(validator))
                .noneMatch(f -> f.getKind() == FactKind.DECLARES_METHOD);
        FileOutline controller = result.getKnowledge().outlineOf(fx.file(
                "src/main/java/com/acme/shop/web/OrderController.java"));
        assertThat(controller.getSymbols().get(0).getAnnotations()).contains("RestController");
    }

    @Test
    void outlinesNonJavaFilesLexically() throws IOException {
        write(repo.resolve("src/app.ts"),
                "export class App { start() {} }");
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new JavaUnderstandingService(config).understand(ranking);

        assertThat(result.getAstFiles()).isZero();
        assertThat(result.getLexicalFiles()).isEqualTo(1);
        assertThat(result.deepOutlines()).hasSize(1);
        FileOutline app = result.getOutlines().get(0);
        assertThat(app.getEngine()).isEqualTo(FileOutline.ENGINE_LEXICAL);
        assertThat(app.typeNames()).contains("App");
        assertThat(app.getResponsibility()).isEqualTo("App class.");
        assertThat(app.getResponsibilitySource()).isEqualTo("heuristic");
    }

    private static List<FileInfo> filesOf(List<FileOutline> outlines) {
        List<FileInfo> files = new ArrayList<>();
        for (FileOutline outline : outlines) {
            files.add(outline.getFile());
        }
        return files;
    }
}
