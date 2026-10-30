package com.deepdoc.understand;

import com.deepdoc.config.LlmSection;
import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsibilityInferrerTest {

    @TempDir
    Path dir;

    @Test
    void prefersJavadocThenNameHeuristic() {
        FileOutline withDoc = outline("OrderService.java", "OrderService",
                "Creates an Order after validation.", DocLevel.DEEP);
        assertThat(ResponsibilityInferrer.heuristic(withDoc, null))
                .isEqualTo("Creates an Order after validation.");

        FileOutline controller = outline("OrdersController.java", "OrdersController", "", DocLevel.SUMMARY);
        assertThat(ResponsibilityInferrer.heuristic(controller, null))
                .isEqualTo("Handles HTTP requests for orders.");

        FileOutline entity = outline("Order.java", "Order", "", DocLevel.SUMMARY);
        assertThat(ResponsibilityInferrer.heuristic(entity, null)).isEqualTo("Order class.");
    }

    @Test
    void humanizesCamelCaseSuffixes() {
        assertThat(ResponsibilityInferrer.humanize("OrderService", "Service")).isEqualTo("order");
        assertThat(ResponsibilityInferrer.humanize("PaymentGateway", "Service")).isEqualTo("payment gateway");
    }

    @Test
    void summaryDoesNotCallTheLlm() {
        boolean[] called = {false};
        LlmClient client = new LlmClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String mode() {
                return "openai";
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                called[0] = true;
                return "Rewritten.";
            }
        };
        FileOutline outline = outline("OrdersController.java", "OrdersController", "", DocLevel.SUMMARY);
        new ResponsibilityInferrer(client).infer(outline, null, null, null, "Unknown", true);
        assertThat(called[0]).isFalse();
        assertThat(outline.getResponsibility()).isEqualTo("Handles HTTP requests for orders.");
        assertThat(outline.getResponsibilitySource()).isEqualTo("heuristic");
    }

    @Test
    void deepLlmRewriteReplacesTheDraft() {
        LlmClient client = new LlmClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String mode() {
                return "openai";
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return "\"Handles checkout.\"";
            }
        };
        FileOutline outline = outline("OrdersController.java", "OrdersController", "", DocLevel.DEEP);
        new ResponsibilityInferrer(client).infer(outline, null, null, null, "Unknown", true);
        assertThat(outline.getResponsibility()).isEqualTo("Handles checkout.");
        assertThat(outline.getResponsibilitySource()).isEqualTo("llm");
    }

    @Test
    void llmFailureKeepsTheDraft() {
        LlmClient failing = new LlmClient() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String mode() {
                return "openai";
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return null;
            }
        };
        FileOutline outline = outline("OrdersController.java", "OrdersController", "", DocLevel.DEEP);
        new ResponsibilityInferrer(failing).infer(outline, null, null, null, "Unknown", true);
        assertThat(outline.getResponsibility()).isEqualTo("Handles HTTP requests for orders.");
        assertThat(outline.getResponsibilitySource()).isEqualTo("heuristic");
    }

    @Test
    void parsesOpenAiResponseAndBuildsARequestWithoutAKeyField() {
        String body = "{ \"choices\": [ { \"message\": { \"content\": \"Handles checkout.\" } } ] }";
        assertThat(OpenAiLlmClient.contentOf(body)).isEqualTo("Handles checkout.");
        assertThat(OpenAiLlmClient.contentOf("{}")).isNull();

        LlmSection section = new LlmSection();
        section.setModel("gpt-4o-mini");
        String json = new OpenAiLlmClient(section).requestJson("system", "user");
        assertThat(json).contains("gpt-4o-mini");
        assertThat(json).doesNotContain("OPENAI_API_KEY");
        assertThat(json).doesNotContain("Bearer");
        assertThat(json).doesNotContain("apiKey");
    }

    private FileOutline outline(String fileName, String typeName, String javadoc, DocLevel level) {
        FileInfo file = new FileInfo(dir.resolve(fileName), "Java", 10, LocalDateTime.now());
        DeclaredSymbol type = new DeclaredSymbol(typeName, SymbolKind.CLASS, 1, "public", javadoc,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
        return new FileOutline(file, level, FileOutline.ENGINE_JAVAPARSER, true,
                "", new ArrayList<>(), Collections.singletonList(type));
    }
}
