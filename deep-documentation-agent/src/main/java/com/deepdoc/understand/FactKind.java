package com.deepdoc.understand;

/**
 * The kinds of fact {@link FactExtractor} writes down from a Java AST.
 *
 * <p>These are the atoms of Day 6-7. The knowledge graph is built only from
 * {@link #DECLARES_TYPE} (who owns a name) and the mention kinds
 * ({@link #REFERENCES}, {@link #EXTENDS}, {@link #IMPLEMENTS}, {@link #IMPORTS}).
 * The rest (methods, annotations, Javadoc) sit on the file so later days can
 * write a paragraph without parsing again. {@link #DECLARES_METHOD} is emitted
 * only for {@code DEEP} files; SUMMARY keeps the other kinds.
 */
public enum FactKind {
    DECLARES_TYPE,
    DECLARES_METHOD,
    EXTENDS,
    IMPLEMENTS,
    ANNOTATED,
    HAS_JAVADOC,
    IMPORTS,
    REFERENCES
}
