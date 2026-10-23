package com.deepdoc.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static com.deepdoc.testutil.ExampleRepos.write;
import static org.assertj.core.api.Assertions.assertThat;

/** One small repository per language, checking that its imports become the expected file edges. */
class MultiLanguageDependencyTest {

    @TempDir
    Path repo;

    @Test
    void python() throws IOException {
        write(repo.resolve("app/__init__.py"), "");
        write(repo.resolve("app/main.py"),
                "import os",
                "from app.services import billing",
                "from .models import User",
                "from . import util",
                "# import app.removed",
                "",
                "def run():",
                "    billing.charge(User())",
                "    billing.refund()",
                "    util.log()");
        write(repo.resolve("app/services/__init__.py"), "");
        write(repo.resolve("app/services/billing.py"), "def charge(u): pass");
        write(repo.resolve("app/models.py"), "class User: pass");
        write(repo.resolve("app/util.py"), "def log(): pass");
        write(repo.resolve("app/removed.py"), "");
        write(repo.resolve("tests/test_main.py"), "from app.main import run");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("app/main.py"))
                .containsExactly("app/models.py", "app/services/billing.py", "app/util.py");
        assertThat(fx.edge("app/main.py", "app/services/billing.py").getReferenceCount()).isEqualTo(2);
        assertThat(fx.graph.getFileGraph().vertexSet())
                .noneMatch(f -> fx.graph.relativePath(f).startsWith("tests/"));
    }

    @Test
    void go() throws IOException {
        write(repo.resolve("go.mod"), "module example.com/shop", "", "go 1.21");
        write(repo.resolve("cmd/server/main.go"),
                "package main",
                "",
                "import (",
                "    \"fmt\"",
                "    \"example.com/shop/internal/store\"",
                "    pay \"example.com/shop/internal/payment\"",
                ")",
                "",
                "func main() {",
                "    s := store.New()",
                "    pay.Charge(s)",
                "    fmt.Println(helper())",
                "}");
        write(repo.resolve("cmd/server/helper.go"), "package main", "", "func helper() string { return \"\" }");
        write(repo.resolve("internal/store/store.go"), "package store", "", "type Store struct{}");
        write(repo.resolve("internal/store/new.go"), "package store", "", "func New() *Store { return &Store{} }");
        write(repo.resolve("internal/payment/payment.go"), "package payment", "", "func Charge(s interface{}) {}");
        write(repo.resolve("internal/payment/payment_test.go"), "package payment");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("cmd/server/main.go")).containsExactly(
                "cmd/server/helper.go", "internal/payment/payment.go", "internal/store/new.go");
        assertThat(fx.targetsOf("internal/store/new.go")).containsExactly("internal/store/store.go");
    }

    @Test
    void csharp() throws IOException {
        write(repo.resolve("src/Api/OrdersController.cs"),
                "using Shop.Domain;",
                "using Repo = Shop.Data.OrderRepository;",
                "namespace Shop.Api",
                "{",
                "    public class OrdersController",
                "    {",
                "        private readonly Repo repo;",
                "        public Order Get() => new Order();",
                "        public Shop.Billing.Invoice Bill() => null;",
                "    }",
                "}");
        write(repo.resolve("src/Domain/Order.cs"), "namespace Shop.Domain;", "public class Order { }");
        write(repo.resolve("src/Data/Repositories.cs"),
                "namespace Shop.Data { public class OrderRepository { } }");
        write(repo.resolve("src/Billing/Invoice.cs"), "namespace Shop.Billing { public record Invoice; }");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("src/Api/OrdersController.cs")).containsExactly(
                "src/Billing/Invoice.cs", "src/Data/Repositories.cs", "src/Domain/Order.cs");
        // Packages are namespaces, with the "Shop" prefix shared by every file stripped.
        assertThat(fx.graph.getPackageGraph().vertexSet())
                .extracting(PackageNode::getName)
                .containsExactlyInAnyOrder("Api", "Billing", "Data", "Domain");
    }

    @Test
    void cAndCpp() throws IOException {
        write(repo.resolve("src/main.c"),
                "#include <stdio.h>",
                "#include \"util.h\"",
                "#include \"net/socket.h\"",
                "int main(void) { return 0; }");
        write(repo.resolve("src/util.h"), "int util(void);");
        write(repo.resolve("include/net/socket.h"), "int open_socket(void);");
        write(repo.resolve("src/engine.cpp"), "#include <net/socket.h>");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("src/main.c")).containsExactly("include/net/socket.h", "src/util.h");
        assertThat(fx.targetsOf("src/engine.cpp")).containsExactly("include/net/socket.h");
    }

    @Test
    void rust() throws IOException {
        write(repo.resolve("Cargo.toml"), "[package]", "name = \"shop\"");
        write(repo.resolve("src/main.rs"),
                "mod config;",
                "mod db;",
                "use std::collections::HashMap;",
                "use crate::db::{pool::Pool, models};",
                "fn main() { let c = config::load(); }");
        write(repo.resolve("src/config.rs"), "pub fn load() {}");
        write(repo.resolve("src/db/mod.rs"), "pub mod pool;", "pub mod models;");
        write(repo.resolve("src/db/pool.rs"), "use super::models::User;", "pub struct Pool;");
        write(repo.resolve("src/db/models.rs"), "pub struct User;");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("src/main.rs")).containsExactly(
                "src/config.rs", "src/db/mod.rs", "src/db/models.rs", "src/db/pool.rs");
        assertThat(fx.targetsOf("src/db/pool.rs")).containsExactly("src/db/models.rs");
    }

    @Test
    void ruby() throws IOException {
        write(repo.resolve("app/controllers/orders_controller.rb"),
                "require_relative '../services/checkout'",
                "class OrdersController < ApplicationController",
                "  def create",
                "    PaymentGateway.charge(Order.new)",
                "  end",
                "end");
        write(repo.resolve("app/services/checkout.rb"), "class Checkout; end");
        write(repo.resolve("app/models/order.rb"), "class Order; end");
        write(repo.resolve("app/models/payment_gateway.rb"), "class PaymentGateway; end");
        write(repo.resolve("lib/tasks/report.rb"), "require 'models/order'");
        write(repo.resolve("spec/orders_spec.rb"), "require 'rails_helper'");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("app/controllers/orders_controller.rb")).containsExactly(
                "app/models/order.rb", "app/models/payment_gateway.rb", "app/services/checkout.rb");
        assertThat(fx.targetsOf("lib/tasks/report.rb")).containsExactly("app/models/order.rb");
    }

    @Test
    void php() throws IOException {
        write(repo.resolve("src/Http/OrderController.php"),
                "<?php",
                "namespace App\\Http;",
                "use App\\Models\\Order;",
                "use App\\Services\\{Mailer, Billing as Bill};",
                "require_once __DIR__ . '/../helpers.php';",
                "class OrderController {",
                "    public function store() { new Order(); new Bill(); new Mailer(); new Request(); }",
                "}");
        write(repo.resolve("src/Http/Request.php"), "<?php", "namespace App\\Http;", "class Request {}");
        write(repo.resolve("src/Models/Order.php"), "<?php", "namespace App\\Models;", "class Order {}");
        write(repo.resolve("src/Services/Mailer.php"), "<?php", "namespace App\\Services;", "class Mailer {}");
        write(repo.resolve("src/Services/Billing.php"), "<?php", "namespace App\\Services;", "class Billing {}");
        write(repo.resolve("src/helpers.php"), "<?php", "function helper() {}");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("src/Http/OrderController.php")).containsExactly(
                "src/Http/Request.php", "src/Models/Order.php", "src/Services/Billing.php",
                "src/Services/Mailer.php", "src/helpers.php");
    }

    @Test
    void swift() throws IOException {
        write(repo.resolve("App/AppMain.swift"),
                "import SwiftUI",
                "@main",
                "struct ShopApp: App {",
                "    let store = CartStore()",
                "    // let old = LegacyView()",
                "}");
        write(repo.resolve("App/CartStore.swift"), "final class CartStore { var items: [Item] = [] }");
        write(repo.resolve("App/Item.swift"), "struct Item {}");
        write(repo.resolve("App/LegacyView.swift"), "struct LegacyView {}");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("App/AppMain.swift")).containsExactly("App/CartStore.swift");
        assertThat(fx.targetsOf("App/CartStore.swift")).containsExactly("App/Item.swift");
    }

    @Test
    void shell() throws IOException {
        write(repo.resolve("bin/deploy.sh"),
                "#!/usr/bin/env bash",
                "source \"$(dirname \"$0\")/../lib/common.sh\"",
                ". ./lib/env.sh",
                "# source lib/old.sh");
        write(repo.resolve("lib/common.sh"), "log() { echo \"$@\"; }");
        write(repo.resolve("lib/env.sh"), "export ENV=prod");
        write(repo.resolve("lib/old.sh"), "");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("bin/deploy.sh")).containsExactly("lib/common.sh", "lib/env.sh");
    }
}
