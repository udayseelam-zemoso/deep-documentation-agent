package com.deepdoc.understand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One type found in a Java file by {@link JavaAstUnderstander}. Methods, annotations,
 * parents and mentioned names hang off it so {@link FactExtractor} can turn the AST
 * walk into a flat list of facts.
 */
public final class DeclaredSymbol {
    private final String name;
    private final SymbolKind kind;
    private final int line;
    private final String visibility;
    private final String javadoc;
    private final List<String> annotations;
    private final List<String> extended;
    private final List<String> implemented;
    private final List<String> methods;
    private final List<String> references;

    public DeclaredSymbol(String name, SymbolKind kind, int line, String visibility, String javadoc,
                          List<String> annotations, List<String> extended, List<String> implemented,
                          List<String> methods, List<String> references) {
        this.name = name;
        this.kind = kind;
        this.line = line;
        this.visibility = visibility == null ? "" : visibility;
        this.javadoc = javadoc == null ? "" : javadoc;
        this.annotations = unmodifiable(annotations);
        this.extended = unmodifiable(extended);
        this.implemented = unmodifiable(implemented);
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

    /** First sentence of the type Javadoc, or empty. */
    public String getJavadoc() {
        return javadoc;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public List<String> getExtended() {
        return extended;
    }

    public List<String> getImplemented() {
        return implemented;
    }

    /** Super types and interfaces, simple names, extends first. */
    public List<String> getParents() {
        List<String> parents = new ArrayList<>(extended);
        parents.addAll(implemented);
        return parents;
    }

    public List<String> getMethods() {
        return methods;
    }

    /** Other type or method names mentioned in this type's body. */
    public List<String> getReferences() {
        return references;
    }

    public boolean isType() {
        return kind == SymbolKind.CLASS || kind == SymbolKind.INTERFACE || kind == SymbolKind.ENUM
                || kind == SymbolKind.RECORD || kind == SymbolKind.ANNOTATION;
    }

    private static List<String> unmodifiable(List<String> values) {
        return values == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
