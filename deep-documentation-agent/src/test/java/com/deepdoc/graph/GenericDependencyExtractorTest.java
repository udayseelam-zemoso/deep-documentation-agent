package com.deepdoc.graph;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.repository.CommitRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.deepdoc.testutil.ExampleRepos.write;
import static org.assertj.core.api.Assertions.assertThat;

/** Languages without a dedicated extractor, handled by {@link GenericDependencyExtractor}. */
class GenericDependencyExtractorTest {

    @TempDir
    Path repo;

    @Test
    void elixirModulesResolveThroughSnakeCasePathsAndUniqueDeclarations() throws IOException {
        write(repo.resolve("lib/shop/application.ex"),
                "defmodule Shop.Application do",
                "  use Application",
                "  alias Shop.CartItem",
                "  def start(_, _), do: Pricing.total(CartItem.new())",
                "end");
        write(repo.resolve("lib/shop/cart_item.ex"), "defmodule Shop.CartItem do", "end");
        write(repo.resolve("lib/pricing_rules.ex"), "defmodule Pricing do", "end");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("lib/shop/application.ex"))
                .containsExactly("lib/pricing_rules.ex", "lib/shop/cart_item.ex");
        assertThat(fx.graph.getGenericFallbackFiles()).isEqualTo(3);
    }

    @Test
    void clojureLuaHaskellAndDartImports() throws IOException {
        write(repo.resolve("src/shop/core.clj"),
                "(ns shop.core",
                "  (:require [shop.cart-utils :as cart]))");
        write(repo.resolve("src/shop/cart_utils.clj"), "(ns shop.cart-utils)");

        write(repo.resolve("game/main.lua"), "local util = require(\"game.util\")");
        write(repo.resolve("game/util.lua"), "return {}");

        write(repo.resolve("hs/Main.hs"), "import Data.Cart (total)", "main = print total");
        write(repo.resolve("hs/Data/Cart.hs"), "module Data.Cart where");

        write(repo.resolve("app/lib/main.dart"),
                "import 'package:shop/src/cart.dart';",
                "import './widgets/button.dart';",
                "// import 'src/removed.dart';",
                "void main() {}");
        write(repo.resolve("app/lib/src/cart.dart"), "class Cart {}");
        write(repo.resolve("app/lib/widgets/button.dart"), "class Button {}");
        write(repo.resolve("app/lib/src/removed.dart"), "class Removed {}");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("src/shop/core.clj")).containsExactly("src/shop/cart_utils.clj");
        assertThat(fx.targetsOf("game/main.lua")).containsExactly("game/util.lua");
        assertThat(fx.targetsOf("hs/Main.hs")).containsExactly("hs/Data/Cart.hs");
        assertThat(fx.targetsOf("app/lib/main.dart"))
                .containsExactly("app/lib/src/cart.dart", "app/lib/widgets/button.dart");
    }

    @Test
    void ambiguousDeclarationsAndOtherLanguagesAreNotLinked() throws IOException {
        write(repo.resolve("a/config.ex"), "defmodule Config do", "end");
        write(repo.resolve("b/config.ex"), "defmodule Config do", "end");
        write(repo.resolve("c/user.ex"), "defmodule User do", "  def x, do: Config.get()", "end");
        write(repo.resolve("d/user.exs"), "User.x()");
        write(repo.resolve("lib/User.dart"), "class User {}");
        write(repo.resolve("lib/main.dart"), "void main() { User(); }");

        GraphFixture fx = GraphFixture.build(repo);

        assertThat(fx.targetsOf("c/user.ex")).isEmpty();
        assertThat(fx.targetsOf("d/user.exs")).containsExactly("c/user.ex");
        assertThat(fx.targetsOf("lib/main.dart")).containsExactly("lib/User.dart");
    }

    @Test
    void filesCommittedTogetherAreLinkedWhenTheCodeShowsNoEdge() throws IOException {
        write(repo.resolve("src/router.erl"), "-module(router).");
        write(repo.resolve("src/handler.erl"), "-module(handler).");
        write(repo.resolve("src/logger.erl"), "-module(logger).");
        write(repo.resolve("src/Api.java"), "class Api {}");

        List<CommitRecord> commits = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            commits.add(commit(i, "src/router.erl", "src/handler.erl", "src/Api.java"));
        }
        // logger changes with router once and alone many times: shared 1 < 3, not coupled.
        commits.add(commit(10, "src/router.erl", "src/logger.erl"));
        for (int i = 0; i < 5; i++) {
            commits.add(commit(20 + i, "src/logger.erl"));
        }

        AppConfig config = ConfigLoader.load();
        GraphFixture fx = GraphFixture.build(repo, config, commits);

        assertThat(fx.targetsOf("src/router.erl")).containsExactly("src/Api.java", "src/handler.erl");
        assertThat(fx.targetsOf("src/handler.erl")).containsExactly("src/Api.java", "src/router.erl");
        assertThat(fx.targetsOf("src/Api.java")).containsExactly("src/handler.erl", "src/router.erl");
        assertThat(fx.targetsOf("src/logger.erl")).isEmpty();
        assertThat(fx.edge("src/router.erl", "src/handler.erl").isCoChange()).isTrue();
        assertThat(fx.edge("src/router.erl", "src/handler.erl").getEvidence()).isNull();
        assertThat(fx.graph.getCoChangeEdges()).isEqualTo(6);

        config.getGraph().setCoChangeEdges(false);
        assertThat(GraphFixture.build(repo, config, commits).graph.getCoChangeEdges()).isZero();
    }

    private static CommitRecord commit(int n, String... paths) {
        return new CommitRecord("c" + n, 1_700_000_000L + n, Arrays.asList(paths));
    }
}
