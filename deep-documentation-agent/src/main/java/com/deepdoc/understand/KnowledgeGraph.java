package com.deepdoc.understand;

import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Day 6-7 knowledge graph: unique type names and the files that mention them.
 *
 * <p>Built from facts, not from import lines. A {@code DECLARES_TYPE} fact whose name appears
 * in exactly one outlined file owns that name. A mention fact ({@code REFERENCES},
 * {@code EXTENDS}, {@code IMPLEMENTS}, {@code IMPORTS}) in another file becomes a <em>uses</em>
 * edge to the owner. Names declared in two files are skipped (ambiguous).
 *
 * <p>This is the same "unique declaration" idea as the generic dependency extractor, applied
 * to the Java files we will actually document, using AST names instead of regex imports.
 */
public final class KnowledgeGraph {
    private final List<FileOutline> outlines;
    private final List<ExtractedFact> facts;
    private final Map<String, FileInfo> uniqueTypes;
    private final Map<FileInfo, Set<FileInfo>> uses = new LinkedHashMap<>();

    public static KnowledgeGraph from(List<FileOutline> outlines) {
        List<ExtractedFact> facts = new ArrayList<>();
        for (FileOutline outline : outlines) {
            facts.addAll(FactExtractor.extract(outline));
        }
        return new KnowledgeGraph(outlines, facts);
    }

    KnowledgeGraph(List<FileOutline> outlines, List<ExtractedFact> facts) {
        this.outlines = Collections.unmodifiableList(new ArrayList<>(outlines));
        this.facts = Collections.unmodifiableList(new ArrayList<>(facts));
        this.uniqueTypes = indexUniqueTypes(facts);
        for (FileOutline outline : outlines) {
            Set<FileInfo> targets = new LinkedHashSet<>();
            for (ExtractedFact fact : facts) {
                if (!fact.getFile().equals(outline.getFile()) || !fact.isMention()) {
                    continue;
                }
                FileInfo target = uniqueTypes.get(fact.mentionName());
                if (target != null && !target.equals(outline.getFile())) {
                    targets.add(target);
                }
            }
            uses.put(outline.getFile(), targets);
        }
    }

    private static Map<String, FileInfo> indexUniqueTypes(List<ExtractedFact> facts) {
        Map<String, List<FileInfo>> all = new LinkedHashMap<>();
        for (ExtractedFact fact : facts) {
            if (fact.getKind() != FactKind.DECLARES_TYPE) {
                continue;
            }
            all.computeIfAbsent(fact.getSubject(), k -> new ArrayList<>()).add(fact.getFile());
        }
        Map<String, FileInfo> unique = new LinkedHashMap<>();
        all.forEach((name, files) -> {
            Set<FileInfo> distinct = new LinkedHashSet<>(files);
            if (distinct.size() == 1) {
                unique.put(name, files.get(0));
            }
        });
        return unique;
    }

    public List<FileOutline> getOutlines() {
        return outlines;
    }

    public List<ExtractedFact> getFacts() {
        return facts;
    }

    public FileOutline outlineOf(FileInfo file) {
        for (FileOutline outline : outlines) {
            if (outline.getFile().equals(file)) {
                return outline;
            }
        }
        return null;
    }

    public List<ExtractedFact> factsOf(FileInfo file) {
        List<ExtractedFact> ofFile = new ArrayList<>();
        for (ExtractedFact fact : facts) {
            if (fact.getFile().equals(file)) {
                ofFile.add(fact);
            }
        }
        return ofFile;
    }

    /** Files whose unique type names this file mentions. */
    public Set<FileInfo> uses(FileInfo file) {
        return uses.getOrDefault(file, Collections.emptySet());
    }

    public Map<String, FileInfo> uniqueTypes() {
        return Collections.unmodifiableMap(uniqueTypes);
    }

    public int typeCount() {
        int count = 0;
        for (FileOutline outline : outlines) {
            count += outline.types().size();
        }
        return count;
    }

    public int methodCount() {
        int count = 0;
        for (FileOutline outline : outlines) {
            count += outline.methodNames().size();
        }
        return count;
    }

    public int useEdgeCount() {
        int count = 0;
        for (Set<FileInfo> targets : uses.values()) {
            count += targets.size();
        }
        return count;
    }
}
