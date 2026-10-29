package com.deepdoc.understand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One type, function or field found in a source file. Java symbols come from the JavaParser AST;
 * other languages come from a lexical scan. The fields that do not apply stay empty.
 */
public final class DeclaredSymbol {
    private final String name;
    private final SymbolKind kind;
    private final int line;
    private final String visibility;
    private final String javadoc;
    private final List<String> annotations;
    private final List<String> parents;
    private final List<String> methods;
    private final List<String> references;

    public DeclaredSymbol(String name, SymbolKind kind, int line, String visibility, String javadoc,
                          List<String> annotations, List<String> parents, List<String> methods,
                          List<String> references) {
        this.name = name;
        this.kind = kind;
        this.line = line;
        this.visibility = visibility == null ? "" : visibility;
        this.javadoc = javadoc == null ? "" : javadoc;
        this.annotations = unmodifiable(annotations);
        this.parents = unmodifiable(parents);
        this.methods = unmodifiable(methods);
        this.references = unmodifiable(references);
    }

    public String getName() {
        return name;
    }

    public SymbolKind getKind() {
        return kind;
    }

    public int getLine() {
        return line;
    }

    public String getVisibility() {
        return visibility;
    }

    /** First sentence of the doc comment, or empty. */
    public String getJavadoc() {
        return javadoc;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    /** Super types / implemented interfaces, simple names. */
    public List<String> getParents() {
        return parents;
    }

    /** Method or function names declared on this type. */
    public List<String> getMethods() {
        return methods;
    }

    /** Other type or function names mentioned in this symbol's body. */
    public List<String> getReferences() {
        return references;
    }

    public boolean isType() {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE || kind == SymbolKind.ENUM
                || kind == SymbolKind.RECORD || kind == SymbolKind.ANNOTATION
                || kind == SymbolKind.STRUCT || kind == SymbolKind.TYPE;
    }

    private static List<String> unmodifiable(List<String> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
