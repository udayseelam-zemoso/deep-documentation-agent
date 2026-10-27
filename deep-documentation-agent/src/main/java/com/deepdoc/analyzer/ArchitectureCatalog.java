package com.deepdoc.analyzer;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The pattern list in {@code architectures.yml}, loaded once per JVM.
 * Adding a framework is a YAML change; {@link ArchitectureDetector} stays the same.
 */
public final class ArchitectureCatalog {
    public static final String RESOURCE_PATH = "/architectures.yml";

    private static volatile ArchitectureCatalog defaultCatalog;

    private final List<ArchitecturePattern> patterns;

    public static class Root {
        private List<ArchitecturePattern> patterns = new ArrayList<>();

        public List<ArchitecturePattern> getPatterns() {
            return patterns;
        }

        public void setPatterns(List<ArchitecturePattern> patterns) {
            this.patterns = patterns == null ? new ArrayList<>() : patterns;
        }
    }

    ArchitectureCatalog(List<ArchitecturePattern> patterns) {
        this.patterns = Collections.unmodifiableList(new ArrayList<>(patterns));
    }

    public static ArchitectureCatalog getDefault() {
        if (defaultCatalog == null) {
            synchronized (ArchitectureCatalog.class) {
                if (defaultCatalog == null) {
                    defaultCatalog = load();
                }
            }
        }
        return defaultCatalog;
    }

    private static ArchitectureCatalog load() {
        try (InputStream input = ArchitectureCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE_PATH);
            }
            Root root = new Yaml(new Constructor(Root.class, new LoaderOptions())).load(input);
            return new ArchitectureCatalog(root == null ? Collections.emptyList() : root.getPatterns());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE_PATH, e);
        }
    }

    public List<ArchitecturePattern> getPatterns() {
        return patterns;
    }
}
