package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JavaAstUnderstanderTest {

    @TempDir
    Path dir;

    @Test
    void readsTypesMethodsJavadocAnnotationsAndReferences() {
        String source = String.join("\n",
                "package com.acme.shop.order;",
                "",
                "import com.acme.shop.domain.Order;",
                "",
                "/** Creates an Order after validation. */",
                "public class OrderService {",
                "    private final OrderValidator validator = null;",
                "    public Order create(Order input) {",
                "        validator.valid(input);",
                "        return input;",
                "    }",
                "}");
        FileOutline outline = outline("OrderService.java", source);

        assertThat(outline.isParsed()).isTrue();
        assertThat(outline.getEngine()).isEqualTo(FileOutline.ENGINE_JAVAPARSER);
        assertThat(outline.getNamespace()).isEqualTo("com.acme.shop.order");
        assertThat(outline.getImports()).contains("com.acme.shop.domain.Order");
        assertThat(outline.typeNames()).containsExactly("OrderService");
        assertThat(outline.methodNames()).contains("create");
        assertThat(outline.primaryJavadoc()).isEqualTo("Creates an Order after validation.");
        assertThat(outline.getSymbols().get(0).getReferences()).contains("Order", "OrderValidator", "valid");
    }

    @Test
    void readsRestControllerAndParents() {
        String source = String.join("\n",
                "package com.acme.web;",
                "@RestController",
                "public class OrdersController extends BaseController implements Ready {",
                "    public String list() { return \"\"; }",
                "}");
        FileOutline outline = outline("OrdersController.java", source);

        DeclaredSymbol type = outline.getSymbols().get(0);
        assertThat(type.getAnnotations()).contains("RestController");
        assertThat(type.getExtended()).contains("BaseController");
        assertThat(type.getImplemented()).contains("Ready");
        assertThat(type.getKind()).isEqualTo(SymbolKind.CLASS);
    }

    @Test
    void stripsJavadocInlineTags() {
        assertThat(JavaAstUnderstander.firstSentence("Uses {@link Order} and {@code @Override}."))
                .isEqualTo("Uses Order and @Override.");
    }

    @Test
    void unparseableSourceIsMarkedFailed() {
        FileOutline outline = outline("Broken.java", "!!!");
        assertThat(outline.isParsed()).isFalse();
        assertThat(outline.getSymbols()).isEmpty();
    }

    private FileOutline outline(String name, String source) {
        FileInfo file = new FileInfo(dir.resolve(name), "Java", 10, LocalDateTime.now());
        return new JavaAstUnderstander().outline(file, DocLevel.DEEP, source);
    }
}
