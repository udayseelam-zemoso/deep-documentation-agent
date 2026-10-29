package com.deepdoc.docs;

import com.deepdoc.model.SourceReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SourceCiteTest {

    @TempDir
    Path repo;

    @Test
    void lineLinkIsRelativeToTheMarkdownFile() {
        Path source = repo.resolve("src/main/java/com/acme/shop/order/OrderService.java");
        Path doc = repo.resolve("docs/packages/order.md");

        String link = SourceCite.lineLink(doc, source, 6, "OrderService.java:6");

        assertThat(link).isEqualTo(
                "[OrderService.java:6](../../src/main/java/com/acme/shop/order/OrderService.java#L6)");
    }

    @Test
    void citeAppendsTheSnippet() {
        Path source = repo.resolve("src/Order.java");
        Path doc = repo.resolve("docs/README.md");
        SourceReference ref = new SourceReference(source, 3, 3, "import com.acme.shop.domain.Order;");

        String cite = SourceCite.cite(doc, repo, ref);

        assertThat(cite).contains("[Order.java:3](../src/Order.java#L3)");
        assertThat(cite).contains("`import com.acme.shop.domain.Order;`");
    }

    @Test
    void displayPathUsesForwardSlashes() {
        Path source = repo.resolve("src/main/java/Order.java");
        assertThat(SourceCite.displayPath(repo, source)).isEqualTo("src/main/java/Order.java");
    }
}
