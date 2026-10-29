package com.deepdoc.understand;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens a {@link FileOutline} into {@link ExtractedFact}s. The AST walk already gathered
 * the raw fields; this step names each one so the knowledge graph and (later) the docs
 * writer share one vocabulary: declares, extends, annotated, references, …
 */
public final class FactExtractor {
    private FactExtractor() {
    }

    public static List<ExtractedFact> extract(FileOutline outline) {
        List<ExtractedFact> facts = new ArrayList<>();
        if (outline == null || !outline.isParsed()) {
            return facts;
        }
        for (String imported : outline.getImports()) {
            facts.add(new ExtractedFact(outline.getFile(), FactKind.IMPORTS, outline.getNamespace(),
                    imported, 0));
        }
        for (DeclaredSymbol type : outline.types()) {
            facts.add(new ExtractedFact(outline.getFile(), FactKind.DECLARES_TYPE, type.getName(),
                    type.getKind().name().toLowerCase(), type.getLine()));
            for (String annotation : type.getAnnotations()) {
                facts.add(new ExtractedFact(outline.getFile(), FactKind.ANNOTATED, type.getName(),
                        annotation, type.getLine()));
            }
            for (String parent : type.getExtended()) {
                facts.add(new ExtractedFact(outline.getFile(), FactKind.EXTENDS, type.getName(),
                        parent, type.getLine()));
            }
            for (String parent : type.getImplemented()) {
                facts.add(new ExtractedFact(outline.getFile(), FactKind.IMPLEMENTS, type.getName(),
                        parent, type.getLine()));
            }
            if (!type.getJavadoc().isEmpty()) {
                facts.add(new ExtractedFact(outline.getFile(), FactKind.HAS_JAVADOC, type.getName(),
                        type.getJavadoc(), type.getLine()));
            }
            for (String method : type.getMethods()) {
                facts.add(new ExtractedFact(outline.getFile(), FactKind.DECLARES_METHOD, type.getName(),
                        method, type.getLine()));
            }
            for (String ref : type.getReferences()) {
                facts.add(new ExtractedFact(outline.getFile(), FactKind.REFERENCES, type.getName(),
                        ref, type.getLine()));
            }
        }
        return facts;
    }
}
