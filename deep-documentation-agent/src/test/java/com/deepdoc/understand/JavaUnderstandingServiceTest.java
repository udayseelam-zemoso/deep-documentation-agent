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

import static com.deepdoc.testutil.ExampleRepos.write;
import static com.deepdoc.testutil.ExampleRepos.writeFourFile;
import static org.assertj.core.api.Assertions.assertThat;

class JavaUnderstandingServiceTest {

    @TempDir
    Path repo;

    @Test
    void outlinesTheFourFileJavaShop() throws IOException {
        writeFourFile(repo);
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        config.getUnderstand().setIncludeListed(true);
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new JavaUnderstandingService(config).understand(ranking);

        assertThat(result.getAstFiles()).isEqualTo(4);
        assertThat(result.getSkippedNonJava()).isZero();
        assertThat(result.getFactCount()).isGreaterThan(0);
        FileOutline controller = result.getKnowledge().outlineOf(fx.file(
                "src/main/java/com/acme/shop/web/OrderController.java"));
        assertThat(controller.getSymbols().get(0).getAnnotations()).contains("RestController");
    }

    @Test
    void skipsNonJavaFiles() throws IOException {
        write(repo.resolve("src/app.ts"),
                "export class App { start() {} }");
        GraphFixture fx = GraphFixture.build(repo);
        AppConfig config = ConfigLoader.load();
        RankingResult ranking = new ImportanceRanker(config.getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        UnderstandingResult result = new JavaUnderstandingService(config).understand(ranking);

        assertThat(result.getAstFiles()).isZero();
        assertThat(result.getSkippedNonJava()).isGreaterThanOrEqualTo(1);
        assertThat(result.getOutlines()).isEmpty();
    }
}
