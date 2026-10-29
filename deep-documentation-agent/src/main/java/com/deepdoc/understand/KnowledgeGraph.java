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
 * Symbols declared in the outlined files, plus which files use a symbol declared in another
 * file. A use is a simple-name match: if {@code OrderService} is declared in one outlined file
 * and mentioned in another, that is an edge. Ambiguous names (declared in two files) are skipped.
 *
 * <p>This is the Week 2 knowledge graph. Week 3 documentation walks it to explain a file
 * in terms of the types it owns and the types it depends on, not only in terms of imports.
 */
public final class KnowledgeGraph {
    private final List<FileOutline> outlines;
    private final Map<String, FileInfo> uniqueTypes;
    private final Map<FileInfo, Set<FileInfo>> uses = new LinkedHashMap<>();

    KnowledgeGraph(List<FileOutline> outlines) {
        this.outlines = Collections.unmodifiableList(new ArrayList<>(outlines));
        this.uniqueTypes = indexUniqueTypes(outlines);
        for (FileOutline outline : outlines) {
            Set<FileInfo> targets = new LinkedHashSet<>();
            for (String name : outline.referencedNames()) {
                FileInfo target = uniqueTypes.get(name);
                if (target != null && !target.equals(outline.getFile())) {
                    targets.add(target);
                }
            }
            uses.put(outline.getFile(), targets);
        }
    }

    private static Map<String, FileInfo> indexUniqueTypes(List<FileOutline> outlines) {
        Map<String, List<FileInfo>> all = new LinkedHashMap<>();
        for (FileOutline outline : outlines) {
            for (String name : outline.typeNames()) {
                all.computeIfAbsent(name, k -> new ArrayList<>()).add(outline.getFile());
            }
        }
        Map<String, FileInfo> unique = new LinkedHashMap<>();
        all.forEach((name, files) -> {
            if (files.size() == 1) unique.put(name, files.get(0));
        });
        return unique;
    }

    public List<FileOutline> getOutlines() {
        return outlines;
    }

    public FileOutline outlineOf(FileInfo file) {
        for (FileOutline outline : outlines) {
            if (outline.getFile().equals(file)) return outline;
        }
        return null;
    }

    /** Files whose unique type names this file mentions. */
    public Set<FileInfo> uses(FileInfo file) {
        return uses.getOrDefault(file, Collections.emptySet());
    }

    public int typeCount() {
        int count = 0;
        for (FileOutline outline : outlines) count += outline.types().size();
        return count;
    }

    public int methodCount() {
        int count = 0;
        for (FileOutline outline : outlines) count += outline.methodNames().size();
        return count;
    }

    public int useEdgeCount() {
        int count = 0;
        for (Set<FileInfo> targets : uses.values()) count += targets.size();
        return count;
    }
}
