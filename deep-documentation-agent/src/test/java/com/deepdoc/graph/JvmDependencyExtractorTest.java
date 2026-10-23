package com.deepdoc.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.deepdoc.testutil.ExampleRepos.write;
import static org.assertj.core.api.Assertions.assertThat;

class JvmDependencyExtractorTest {

    @TempDir
    Path repo;

    @Test
    void detectsFullyQualifiedNamesWithoutImport() throws IOException {
        write(repo.resolve("src/com/acme/util/Money.java"),
                "package com.acme.util;",
                "public class Money { public static class Currency {} }");
        write(repo.resolve("src/com/acme/billing/Invoice.java"),
                "package com.acme.billing;",
                "public class Invoice {",
                "    // com.acme.util.Ignored in a comment does not count",
                "    private com.acme.util.Money total = new com.acme.util.Money();",
                "    private com.acme.util.Money.Currency currency;",
                "    private String note = \"com.acme.util.Money\";",
                "    int size() { return this.items.size(); }",
                "}");

        GraphFixture fx = GraphFixture.build(repo);

        DependencyEdge edge = fx.edge("src/com/acme/billing/Invoice.java", "src/com/acme/util/Money.java");
        assertThat(edge).isNotNull();
        assertThat(edge.getReferenceCount()).isEqualTo(3);
        assertThat(edge.getEvidence().getStartLine()).isEqualTo(4);
    }

    @Test
    void linksKotlinScalaAndJavaThroughOnePackageIndex() throws IOException {
        write(repo.resolve("src/com/acme/domain/Order.java"),
                "package com.acme.domain;",
                "public class Order {}");
        write(repo.resolve("src/com/acme/domain/Models.kt"),
                "package com.acme.domain",
                "",
                "data class LineItem(val sku: String)",
                "sealed class Status",
                "fun Order.total(): Int = 0");
        write(repo.resolve("src/com/acme/service/OrderService.kt"),
                "package com.acme.service",
                "",
                "import com.acme.domain.Order",
                "import com.acme.domain.LineItem as Item",
                "import com.acme.domain.total",
                "",
                "class OrderService {",
                "    fun place(o: Order, items: List<Item>) = o.total()",
                "}");
        write(repo.resolve("src/com/acme/report/Report.scala"),
                "package com.acme.report",
                "",
                "import com.acme.domain.{Order, Status => OrderStatus}",
                "import com.acme.service._",
                "",
                "class Report(service: OrderService, order: Order, status: OrderStatus)");

        GraphFixture fx = GraphFixture.build(repo);

        // Order.java, and Models.kt for both LineItem (aliased) and the extension function
        assertThat(fx.targetsOf("src/com/acme/service/OrderService.kt"))
                .containsExactly("src/com/acme/domain/Models.kt", "src/com/acme/domain/Order.java");
        assertThat(fx.edge("src/com/acme/service/OrderService.kt", "src/com/acme/domain/Models.kt")
                .getReferenceCount()).isEqualTo(2);
        assertThat(fx.targetsOf("src/com/acme/report/Report.scala")).containsExactly(
                "src/com/acme/domain/Models.kt", "src/com/acme/domain/Order.java",
                "src/com/acme/service/OrderService.kt");
        // Kotlin files are grouped by their package declaration, like Java
        assertThat(fx.graph.packageOf(fx.file("src/com/acme/domain/Models.kt")))
                .isEqualTo(fx.graph.packageOf(fx.file("src/com/acme/domain/Order.java")));
    }
}
