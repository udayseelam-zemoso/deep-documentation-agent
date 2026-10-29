package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FactExtractorTest {

    @TempDir
    Path dir;

    @Test
    void emitsDeclareExtendAnnotateAndReferenceFacts() {
        String source = String.join("\n",
                "package com.acme.web;",
                "import com.acme.shop.order.OrderService;",
                "/** Handles HTTP requests for orders. */",
                "@RestController",
                "public class OrdersController extends BaseController {",
                "    private OrderService service;",
                "    public String list() { return service.toString(); }",
                "}");
        FileOutline outline = new JavaAstUnderstander().outline(
                new FileInfo(dir.resolve("OrdersController.java"), "Java", 8, LocalDateTime.now()),
                DocLevel.DEEP, source);

        List<ExtractedFact> facts = FactExtractor.extract(outline);

        assertThat(facts).anyMatch(f -> f.getKind() == FactKind.DECLARES_TYPE
                && f.getSubject().equals("OrdersController"));
        assertThat(facts).anyMatch(f -> f.getKind() == FactKind.DECLARES_METHOD
                && f.getObject().equals("list"));
        assertThat(facts).anyMatch(f -> f.getKind() == FactKind.ANNOTATED
                && f.getObject().equals("RestController"));
        assertThat(facts).anyMatch(f -> f.getKind() == FactKind.EXTENDS
                && f.getObject().equals("BaseController"));
        assertThat(facts).anyMatch(f -> f.getKind() == FactKind.HAS_JAVADOC
                && f.getObject().startsWith("Handles HTTP"));
        assertThat(facts).anyMatch(f -> f.getKind() == FactKind.IMPORTS
                && f.getObject().endsWith("OrderService"));
        assertThat(facts).anyMatch(f -> f.getKind() == FactKind.REFERENCES
                && f.getObject().equals("OrderService"));
    }

    @Test
    void failedOutlineYieldsNoFacts() {
        FileOutline outline = new JavaAstUnderstander().outline(
                new FileInfo(dir.resolve("Broken.java"), "Java", 1, LocalDateTime.now()),
                DocLevel.DEEP, "!!!");
        assertThat(FactExtractor.extract(outline)).isEmpty();
    }
}
