package com.deepdoc.graph;

import java.util.HashMap;
import java.util.Map;

/**
 * Languages that share one way of expressing dependencies, and therefore one extractor.
 * Language names are the ones from {@code languages.yml}.
 */
public enum LanguageFamily {
    /** Java, Kotlin, Scala, Groovy: packages and imports. */
    JVM(SourceScanner.Syntax.C_LIKE, "Java", "Kotlin", "Scala", "Groovy"),
    /** JavaScript and TypeScript: module specifiers in import/require. */
    JAVASCRIPT(SourceScanner.Syntax.JS, "JavaScript", "TypeScript");

    private static final Map<String, LanguageFamily> BY_LANGUAGE = new HashMap<>();

    static {
        for (LanguageFamily family : values()) {
            for (String language : family.languages) {
                BY_LANGUAGE.put(language, family);
            }
        }
    }

    private final SourceScanner.Syntax syntax;
    private final String[] languages;

    LanguageFamily(SourceScanner.Syntax syntax, String... languages) {
        this.syntax = syntax;
        this.languages = languages;
    }

    /** Comment and string syntax, for stripping comments before matching. */
    public SourceScanner.Syntax syntax() {
        return syntax;
    }

    /** Family of a language name, or null if no extractor understands it. */
    public static LanguageFamily of(String language) {
        return language == null ? null : BY_LANGUAGE.get(language);
    }
}
