package com.deepdoc.utils;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lookup tables built from {@code languages.yml}: extension, exact file name, file-name prefix
 * and {@code #!} interpreter, each mapping to a {@link LanguageDefinition}.
 * Loaded once per JVM because the file is part of the build.
 */
public final class LanguageCatalog {
    public static final String RESOURCE_PATH = "/languages.yml";

    private static volatile LanguageCatalog defaultCatalog;

    private final Map<String, LanguageDefinition> byName = new HashMap<>();
    private final Map<String, LanguageDefinition> byExtension = new HashMap<>();
    private final Map<String, LanguageDefinition> byFilename = new HashMap<>();
    private final Map<String, LanguageDefinition> byFilenamePrefix = new HashMap<>();
    private final Map<String, LanguageDefinition> byInterpreter = new HashMap<>();

    /** Root object of {@code languages.yml}. */
    public static class Root {
        private List<LanguageDefinition> languages = new ArrayList<>();

        public List<LanguageDefinition> getLanguages() {
            return languages;
        }

        public void setLanguages(List<LanguageDefinition> languages) {
            this.languages = languages;
        }
    }

    LanguageCatalog(List<LanguageDefinition> languages) {
        for (LanguageDefinition language : languages) {
            // Fails fast on a typo in "type" instead of at the first file of that language.
            language.languageType();
            byName.put(language.getName(), language);
            for (String ext : language.getExtensions()) {
                byExtension.put(ext.toLowerCase(Locale.ROOT), language);
            }
            for (String filename : language.getFilenames()) {
                if (filename.endsWith(".*")) {
                    byFilenamePrefix.put(filename.substring(0, filename.length() - 1), language);
                } else {
                    byFilename.put(filename, language);
                }
            }
            for (String interpreter : language.getInterpreters()) {
                byInterpreter.put(interpreter, language);
            }
        }
    }

    public static LanguageCatalog getDefault() {
        if (defaultCatalog == null) {
            synchronized (LanguageCatalog.class) {
                if (defaultCatalog == null) {
                    defaultCatalog = load();
                }
            }
        }
        return defaultCatalog;
    }

    private static LanguageCatalog load() {
        try (InputStream input = LanguageCatalog.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE_PATH);
            }
            Root root = new Yaml(new Constructor(Root.class, new LoaderOptions())).load(input);
            return new LanguageCatalog(root == null ? Collections.emptyList() : root.getLanguages());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE_PATH, e);
        }
    }

    public LanguageDefinition byName(String name) {
        return byName.get(name);
    }

    /** Exact file-name match first, then the longest registered prefix ending in a dot. */
    LanguageDefinition byFilename(String filename) {
        LanguageDefinition exact = byFilename.get(filename);
        if (exact != null) {
            return exact;
        }
        LanguageDefinition best = null;
        int bestLength = -1;
        for (Map.Entry<String, LanguageDefinition> e : byFilenamePrefix.entrySet()) {
            if (filename.startsWith(e.getKey()) && e.getKey().length() > bestLength) {
                best = e.getValue();
                bestLength = e.getKey().length();
            }
        }
        return best;
    }

    /** @param extension including the leading dot, any case */
    LanguageDefinition byExtension(String extension) {
        return byExtension.get(extension.toLowerCase(Locale.ROOT));
    }

    LanguageDefinition byInterpreter(String interpreter) {
        return byInterpreter.get(interpreter);
    }
}
