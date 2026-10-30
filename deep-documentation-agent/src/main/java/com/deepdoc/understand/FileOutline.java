package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Structured view of one source file: namespace, imports, and the types it declares.
 * {@link #getLevel()} is the Week 1 documentation level (DEEP / SUMMARY / LISTED)
 * and decides how many facts {@link FactExtractor} keeps. After
 * {@link ResponsibilityInferrer} runs, {@link #getResponsibility()} is the one sentence
 * Week 3 will open the file's section with.
 */
public final class FileOutline {
    public static final String ENGINE_JAVAPARSER = "javaparser";
    public static final String ENGINE_LEXICAL = "lexical";

    private final FileInfo file;
    private final DocLevel level;
    private final String engine;
    private final boolean parsed;
    private final String namespace;
    private final List<String> imports;
    private final List<DeclaredSymbol> symbols;
    /** Filled by {@link ResponsibilityInferrer}; empty until then. */
    private String responsibility = "";
    /** {@code javadoc}, {@code heuristic}, or {@code llm}. */
    private String responsibilitySource = "";

    public FileOutline(FileInfo file, DocLevel level, String engine, boolean parsed,
                       String namespace, List<String> imports, List<DeclaredSymbol> symbols) {
        this.file = file;
        this.level = level;
        this.engine = engine;
        this.parsed = parsed;
        this.namespace = namespace == null ? "" : namespace;
        this.imports = imports == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(imports));
        this.symbols = symbols == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(symbols));
    }

    public FileInfo getFile() {
        return file;
    }

    public DocLevel getLevel() {
        return level;
    }

    public String getEngine() {
        return engine;
    }

    public boolean isParsed() {
        return parsed;
    }

    public String getNamespace() {
        return namespace;
    }

    public List<String> getImports() {
        return imports;
    }

    public List<DeclaredSymbol> getSymbols() {
        return symbols;
    }

    public String getResponsibility() {
        return responsibility;
    }

    /** {@code javadoc}, {@code heuristic}, or {@code llm}. Empty until inference runs. */
    public String getResponsibilitySource() {
        return responsibilitySource;
    }

    public void setResponsibility(String responsibility, String source) {
        this.responsibility = responsibility == null ? "" : responsibility.trim();
        this.responsibilitySource = source == null ? "" : source;
    }

    public List<DeclaredSymbol> types() {
        List<DeclaredSymbol> types = new ArrayList<>();
        for (DeclaredSymbol symbol : symbols) {
            if (symbol.isType()) {
                types.add(symbol);
            }
        }
        return types;
    }

    public List<String> typeNames() {
        List<String> names = new ArrayList<>();
        for (DeclaredSymbol symbol : types()) {
            names.add(symbol.getName());
        }
        return names;
    }

    public List<String> methodNames() {
        Set<String> names = new LinkedHashSet<>();
        for (DeclaredSymbol symbol : symbols) {
            if (symbol.getKind() == SymbolKind.METHOD || symbol.getKind() == SymbolKind.FUNCTION
                    || symbol.getKind() == SymbolKind.CONSTRUCTOR) {
                names.add(symbol.getName());
            }
            names.addAll(symbol.getMethods());
        }
        return new ArrayList<>(names);
    }

    public String primaryJavadoc() {
        for (DeclaredSymbol symbol : symbols) {
            if (!symbol.getJavadoc().isEmpty()) {
                return symbol.getJavadoc();
            }
        }
        return "";
    }
}
