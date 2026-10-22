package com.deepdoc.graph;

import com.deepdoc.config.GraphSection;
import com.deepdoc.model.FileInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PackageGrouperTest {

    private int counter;

    private void add(Map<FileInfo, PackageGrouper.Key> keys, String pkg, int files) {
        for (int i = 0; i < files; i++) {
            FileInfo file = new FileInfo(Paths.get("/repo/F" + (counter++) + ".java"), "Java", 10, LocalDateTime.now());
            keys.put(file, new PackageGrouper.Key(PackageGrouper.Kind.JAVA_PACKAGE, Arrays.asList(pkg.split("\\."))));
        }
    }

    @Test
    void increasesDepthUntilEnoughPackages() {
        Map<FileInfo, PackageGrouper.Key> keys = new LinkedHashMap<>();
        add(keys, "com.acme.api.rest", 1);
        add(keys, "com.acme.api.grpc", 1);
        add(keys, "com.acme.api.auth", 1);
        add(keys, "com.acme.core.model", 1);
        add(keys, "com.acme.core.service", 1);
        add(keys, "com.acme.core.util", 1);

        PackageGrouper.Result result = new PackageGrouper(new GraphSection()).group(keys);

        assertThat(result.getStrippedPrefixes()).containsExactly("com.acme");
        assertThat(result.getDepth()).isEqualTo(2);
        assertThat(result.getGroupOf().values()).contains("api.rest", "core.util");
    }

    @Test
    void splitsAGroupHoldingTooManyFiles() {
        Map<FileInfo, PackageGrouper.Key> keys = new LinkedHashMap<>();
        add(keys, "com.acme.billing.invoice", 15);
        add(keys, "com.acme.billing.tax", 15);
        add(keys, "com.acme.auth", 2);
        add(keys, "com.acme.web", 2);
        add(keys, "com.acme.util", 2);
        add(keys, "com.acme.report", 2);

        PackageGrouper.Result result = new PackageGrouper(new GraphSection()).group(keys);

        assertThat(result.getDepth()).isEqualTo(1);
        assertThat(result.getGroupOf().values()).doesNotContain("billing")
                .contains("billing.invoice", "billing.tax", "auth", "web");
    }

    @Test
    void keepsLastSegmentWhenAllFilesShareOnePackage() {
        Map<FileInfo, PackageGrouper.Key> keys = new LinkedHashMap<>();
        add(keys, "com.acme.app", 3);

        PackageGrouper.Result result = new PackageGrouper(new GraphSection()).group(keys);

        assertThat(result.getGroupOf().values()).containsOnly("app");
    }
}
