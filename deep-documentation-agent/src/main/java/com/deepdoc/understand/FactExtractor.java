package com.deepdoc.understand;

import com.deepdoc.ranker.DocLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens a {@link FileOutline} into {@link ExtractedFact}s. How many facts we keep depends
 * on the file's documentation level from Week 1:
 *
 * <ul>
 *   <li><b>DEEP</b> — full set, including every method. That is the raw material for a
 *       per-file section (purpose, key flows).</li>
 *   <li><b>SUMMARY</b> — type, Javadoc, annotations, parents, imports and references.
 *       Enough for a paragraph. Method lists stay off so a SUMMARY file does not look
 *       like a DEEP one. References still feed the knowledge graph.</li>
 *   <li><b>LISTED</b> — not extracted (those files are not outlined).</li>
 * </ul>
 */
public final class FactExtractor {
    private FactExtractor() {
    }

    public static List<ExtractedFact> extract(FileOutline outline) {
        List<ExtractedFact> facts = new ArrayList<>();
        if (outline == null || !outline.isParsed()) {
            return facts;
        }
        boolean deep = outline.getLevel() == DocLevel.DEEP;
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
            if (deep) {
                for (String method : type.getMethods()) {
                    facts.add(new ExtractedFact(outline.getFile(), FactKind.DECLARES_METHOD, type.getName(),
                            method, type.getLine()));
                }
            }
            for (String ref : type.getReferences()) {
                facts.add(new ExtractedFact(outline.getFile(), FactKind.REFERENCES, type.getName(),
                        ref, type.getLine()));
            }
        }
        return facts;
    }
}
