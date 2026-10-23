package com.deepdoc.graph;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TestFileDetectorTest {

    @Test
    void recognisesTestNamingConventions() {
        for (String path : new String[] {
                "src/main/java/a/OrderServiceTest.java", "app/src/OrderSpec.kt", "web/cart.test.tsx",
                "pkg/test_billing.py", "pkg/billing_test.py", "conftest.py", "store/store_test.go",
                "Shop/OrderTests.cs", "src/OrderTest.php", "App/CartTests.swift", "lib/order_spec.rb",
                "spec/models/order.rb", "src/parser_test.cpp", "scripts/deploy.bats", "fixtures/testdata/a.go"}) {
            assertThat(TestFileDetector.isTest(Paths.get(path))).as(path).isTrue();
        }
    }

    @Test
    void leavesApplicationCodeAlone() {
        for (String path : new String[] {
                "src/main/java/a/TestimonialService.java", "pkg/testing_utils.py", "store/store.go",
                "lib/specification.rb", "src/contest.c", "App/Tests.swift.orig"}) {
            assertThat(TestFileDetector.isTest(Paths.get(path))).as(path).isFalse();
        }
    }
}
