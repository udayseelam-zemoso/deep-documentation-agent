package com.deepdoc.testutil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Writes small, hand-checked example repositories used by graph and ranking tests. */
public final class ExampleRepos {
    private static final String BASE = "com.acme.shop";

    private ExampleRepos() {
    }

    /**
     * Ten production files in six top-level packages (plus one test file).
     *
     * <pre>
     * web.OrderController      -> order.service.OrderService, domain.Order
     * web.PaymentController    -> payment.PaymentService, domain.Payment
     * order.service.OrderService -> order.validation.OrderValidator, persistence.OrderRepository,
     *                               payment.PaymentService, domain.Order, util.Money
     * order.validation.OrderValidator -> domain.Order, util.Money
     * payment.PaymentService   -> persistence.PaymentRepository, domain.Payment, util.Money
     * persistence.OrderRepository   -> domain.Order
     * persistence.PaymentRepository -> domain.Payment
     * domain.Order             -> util.Money
     * domain.Payment           -> util.Money
     * util.Money
     * </pre>
     */
    public static void writeShop(Path root) throws IOException {
        javaClass(root, "web", "OrderController", "@RestController",
                "order.service.OrderService", "domain.Order");
        javaClass(root, "web", "PaymentController", "@RestController",
                "payment.PaymentService", "domain.Payment");
        javaClass(root, "order.service", "OrderService", "",
                "order.validation.OrderValidator", "persistence.OrderRepository",
                "payment.PaymentService", "domain.Order", "util.Money");
        javaClass(root, "order.validation", "OrderValidator", "", "domain.Order", "util.Money");
        javaClass(root, "payment", "PaymentService", "",
                "persistence.PaymentRepository", "domain.Payment", "util.Money");
        javaClass(root, "persistence", "OrderRepository", "", "domain.Order");
        javaClass(root, "persistence", "PaymentRepository", "", "domain.Payment");
        javaClass(root, "domain", "Order", "", "util.Money");
        javaClass(root, "domain", "Payment", "", "util.Money");
        javaClass(root, "util", "Money", "");

        write(root.resolve("src/test/java/com/acme/shop/order/service/OrderServiceTest.java"),
                "package com.acme.shop.order.service;",
                "import com.acme.shop.domain.Order;",
                "class OrderServiceTest { OrderService s; Order o; }");
    }

    /**
     * Four files, three packages. OrderService uses Order four times and OrderValidator
     * (same package, so no import) once.
     *
     * <pre>
     * web.OrderController -> order.OrderService (1)
     * order.OrderService  -> domain.Order (4), order.OrderValidator (1)
     * order.OrderValidator -> domain.Order (1)
     * </pre>
     */
    public static void writeFourFile(Path root) throws IOException {
        Path src = root.resolve("src/main/java/com/acme/shop");
        write(src.resolve("web/OrderController.java"),
                "package com.acme.shop.web;",
                "",
                "import com.acme.shop.order.OrderService;",
                "",
                "@RestController",
                "public class OrderController {",
                "    private OrderService service;",
                "}");
        write(src.resolve("order/OrderService.java"),
                "package com.acme.shop.order;",
                "",
                "import com.acme.shop.domain.Order;",
                "",
                "/** Creates an Order after validation. */",
                "public class OrderService {",
                "    private final OrderValidator validator = null;",
                "    public Order create(Order input) {",
                "        Order copy = input;",
                "        return (Order) copy;",
                "    }",
                "}");
        write(src.resolve("order/OrderValidator.java"),
                "package com.acme.shop.order;",
                "",
                "import com.acme.shop.domain.Order;",
                "",
                "public class OrderValidator {",
                "    public boolean valid(Order order) { return order != null; }",
                "}");
        write(src.resolve("domain/Order.java"),
                "package com.acme.shop.domain;",
                "",
                "public class Order {",
                "    private String id = \"Order-1\";",
                "}");
    }

    private static void javaClass(Path root, String pkg, String name, String annotation, String... imports)
            throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("package " + BASE + "." + pkg + ";");
        lines.add("");
        for (String imp : imports) {
            lines.add("import " + BASE + "." + imp + ";");
        }
        lines.add("");
        if (!annotation.isEmpty()) {
            lines.add(annotation);
        }
        lines.add("public class " + name + " {");
        int i = 0;
        for (String imp : imports) {
            String simple = imp.substring(imp.lastIndexOf('.') + 1);
            lines.add("    private " + simple + " field" + (i++) + ";");
        }
        lines.add("}");
        Path file = root.resolve("src/main/java/" + (BASE + "." + pkg).replace('.', '/') + "/" + name + ".java");
        write(file, lines.toArray(new String[0]));
    }

    public static void write(Path file, String... lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, String.join("\n", lines).concat("\n").getBytes());
    }
}
