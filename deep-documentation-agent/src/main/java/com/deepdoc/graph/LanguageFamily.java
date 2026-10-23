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
    /** JavaScript, TypeScript and Vue/Svelte components: module specifiers in import/require. */
    JAVASCRIPT(SourceScanner.Syntax.JS, "JavaScript", "TypeScript", "Vue", "Svelte"),
    /** Python: dotted module paths in import / from-import, including relative imports. */
    PYTHON(SourceScanner.Syntax.PYTHON, "Python"),
    /** Go: import paths resolved against go.mod, plus package-qualified identifiers. */
    GO(SourceScanner.Syntax.GO, "Go"),
    /** C#: namespaces, using directives and type names (types are not tied to file names). */
    CSHARP(SourceScanner.Syntax.C_LIKE, "C#"),
    /** C, C++ and Objective-C: #include / #import of header files. */
    C_FAMILY(SourceScanner.Syntax.C_LIKE, "C", "C++", "C/C++", "Objective-C"),
    /** Rust: mod declarations and use paths, resolved through the crate's module tree. */
    RUST(SourceScanner.Syntax.RUST, "Rust"),
    /** Ruby: require / require_relative and Rails-style autoloaded constants. */
    RUBY(SourceScanner.Syntax.RUBY, "Ruby"),
    /** PHP: namespaces, use statements and include / require. */
    PHP(SourceScanner.Syntax.PHP, "PHP"),
    /** Swift: no file-level imports inside a module, so edges come from type names. */
    SWIFT(SourceScanner.Syntax.SWIFT, "Swift"),
    /** Shell scripts: files pulled in with source or the dot command. */
    SHELL(SourceScanner.Syntax.SHELL, "Shell");

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
