package com.deepdoc.understand;

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
        FileOutline withDoc = outline("OrderService.java", "OrderService", "Creates an Order after validation.");
        assertThat(ResponsibilityInferrer.heuristic(withDoc, null))
                .isEqualTo("Creates an Order after validation.");

        FileOutline controller = outline("OrdersController.java", "OrdersController", "");
        assertThat(ResponsibilityInferrer.heuristic(controller, null))
                .isEqualTo("Handles HTTP requests for orders.");

        FileOutline entity = outline("Order.java", "Order", "");
        assertThat(ResponsibilityInferrer.heuristic(entity, null)).isEqualTo("Order class.");
    }

    @Test
    void humanizesCamelCaseSuffixes() {
        assertThat(ResponsibilityInferrer.humanize("OrderService", "Service")).isEqualTo("order");
        assertThat(ResponsibilityInferrer.humanize("PaymentGateway", "Service")).isEqualTo("payment gateway");
    }

    @Test
    void llmFailureKeepsHeuristic() {
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
        FileOutline outline = outline("OrdersController.java", "OrdersController", "");
        new ResponsibilityInferrer(failing).infer(outline, null, null, null, "Unknown", true);
        assertThat(outline.getResponsibility()).isEqualTo("Handles HTTP requests for orders.");
        assertThat(outline.getResponsibilitySource()).isEqualTo("heuristic");
    }

    @Test
    void parsesOpenAiResponseAndBuildsARequestWithoutAKeyField() {
        String body = "{ \"choices\": [ { \"message\": { \"content\": \"Handles checkout.\" } } ] }";
        assertThat(OpenAiLlmClient.contentOf(body)).isEqualTo("Handles checkout.");
        assertThat(OpenAiLlmClient.contentOf("{}")).isNull();
    }

    private FileOutline outline(String fileName, String typeName, String javadoc) {
        FileInfo file = new FileInfo(dir.resolve(fileName), "Java", 10, LocalDateTime.now());
        DeclaredSymbol type = new DeclaredSymbol(typeName, SymbolKind.CLASS, 1, "public", javadoc,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        FileOutline outline = new FileOutline(file, DocLevel.DEEP, FileOutline.ENGINE_JAVAPARSER, true,
                "", new ArrayList<>(), Collections.singletonList(type));
        return outline;
    }
}
