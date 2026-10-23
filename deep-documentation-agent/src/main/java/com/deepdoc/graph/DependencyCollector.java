package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates the dependencies of one source file. Self references and unresolved (null)
 * targets are ignored, so extractors can pass resolution results straight in.
 */
final class DependencyCollector {
    private final FileInfo from;
    /** target -> {reference count, line of first evidence} */
    private final Map<FileInfo, int[]> found = new LinkedHashMap<>();

    DependencyCollector(FileInfo from) {
        this.from = from;
    }

    /** Adds references; repeated calls for the same target sum up and keep the first line. */
    void add(FileInfo target, int references, int line) {
        if (target == null || target.equals(from) || references <= 0) return;
        int[] entry = found.get(target);
        if (entry == null) {
            found.put(target, new int[]{references, line});
        } else {
            entry[0] += references;
        }
    }

    /**
     * Adds the target only if no earlier step found it. Used when two detection steps would
     * count the same usages twice (an import and the same-package scan both see {@code Order}).
     */
    void addOnce(FileInfo target, int references, int line) {
        if (target != null && !found.containsKey(target)) {
            add(target, references, line);
        }
    }

    boolean contains(FileInfo target) {
        return found.containsKey(target);
    }

    List<RawDependency> toList() {
        List<RawDependency> result = new ArrayList<>();
        found.forEach((target, v) -> result.add(new RawDependency(from, target, v[0], v[1])));
        return result;
    }
}
