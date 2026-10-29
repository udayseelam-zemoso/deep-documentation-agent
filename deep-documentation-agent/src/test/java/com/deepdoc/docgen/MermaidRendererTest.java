package com.deepdoc.docgen;

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

class MermaidRendererTest {

    @TempDir
    Path repo;

    @Test
    void packageFlowDrawsTheFourFileShop() throws IOException {
        writeFourFile(repo);
        GraphFixture fx = GraphFixture.build(repo);
        RankingResult ranking = new ImportanceRanker(ConfigLoader.load().getRanking())
                .rank(fx.graph, ActivityScores.none(), EntryPointDetector.detect(fx.graph));

        String mermaid = MermaidRenderer.packageFlow(fx.graph, ranking);

        assertThat(mermaid).startsWith("flowchart LR");
        assertThat(mermaid).contains("web");
        assertThat(mermaid).contains("order");
        assertThat(mermaid).contains("domain");
        assertThat(mermaid).contains("web --> order");
        assertThat(mermaid).contains("order --> domain");
        assertThat(mermaid).contains("ORCHESTRATOR");
        assertThat(mermaid).contains("FOUNDATION");
    }

    @Test
    void nodeIdStripsDotsAndSlashes() {
        assertThat(MermaidRenderer.nodeId("order.service")).isEqualTo("order_service");
        assertThat(MermaidRenderer.nodeId("src/api/")).isEqualTo("src_api");
        assertThat(MermaidRenderer.nodeId("2web")).isEqualTo("pkg_2web");
    }

    @Test
    void emptyRankingProducesNoDiagram() {
        assertThat(MermaidRenderer.packageFlow(null, null)).isEmpty();
    }
}
