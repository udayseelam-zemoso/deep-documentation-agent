package com.deepdoc.graph;

import com.deepdoc.config.GraphSection;
import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groups files into packages at an automatically chosen depth. The package graph is what a
 * reader sees first ("the system has these 12 modules"), so it must have a readable number of
 * nodes: two packages say nothing, two hundred are unreadable. Rather than ask the user for a
 * depth, the grouper picks one per repository.
 *
 * <ol>
 *   <li>Strip the prefix shared by every file of a kind ({@code com.acme.shop}, {@code src}).</li>
 *   <li>Take the first {@code d} remaining segments as the group, growing {@code d} from 1 until
 *       there are at least {@code minPackages} groups (backing off if that overshoots {@code maxPackages}).</li>
 *   <li>Split any group that still holds more than {@code splitFraction} of all files one level deeper.</li>
 * </ol>
 * Files in languages with declared namespaces (Java/Kotlin packages, C# and PHP namespaces) are
 * keyed by that namespace, everything else by directory. Directory groups are displayed with a
 * trailing {@code /} so they never collide with namespace groups of the same name.
 *
 * <p>Example: files under {@code com.acme.shop.order.api}, {@code com.acme.shop.order.db} and
 * {@code com.acme.shop.billing} lose the shared {@code com.acme.shop}; at depth 1 they form
 * {@code order} and {@code billing}. If {@code order} held most of the repository it would be
 * split into {@code order.api} and {@code order.db}.
 */
public final class PackageGrouper {
    /** Name of the group for files sitting directly at the stripped prefix (e.g. the repository root). */
    public static final String ROOT = "(root)";

    /** How a file's package key was obtained, which decides its separator and display form. */
    public enum Kind {
        /** Dotted namespace such as {@code com.acme.order}. */
        NAMESPACE("."),
        /** Directory path relative to the repository root. */
        DIRECTORY("/");

        final String separator;

        Kind(String separator) {
            this.separator = separator;
        }
    }

    /** Package key of one file: its namespace or directory, split into segments. */
    public static final class Key {
        final Kind kind;
        final List<String> segments;

        public Key(Kind kind, List<String> segments) {
            this.kind = kind;
            this.segments = segments;
        }
    }

    /** File-to-group assignment plus what was decided along the way, for reporting. */
    public static final class Result {
        private final Map<FileInfo, String> groupOf;
        private final int depth;
        private final List<String> strippedPrefixes;

        Result(Map<FileInfo, String> groupOf, int depth, List<String> strippedPrefixes) {
            this.groupOf = groupOf;
            this.depth = depth;
            this.strippedPrefixes = strippedPrefixes;
        }

        public Map<FileInfo, String> getGroupOf() {
            return groupOf;
        }

        /** Base depth chosen in step 2; split groups are one or more levels deeper. */
        public int getDepth() {
            return depth;
        }

        /** Prefixes removed in step 1, one per kind that had one (e.g. "com.acme.shop", "src"). */
        public List<String> getStrippedPrefixes() {
            return strippedPrefixes;
        }
    }

    /** Safety bound on step 3; each round splits at most one group. */
    private static final int MAX_SPLIT_ROUNDS = 20;

    private final GraphSection config;

    public PackageGrouper(GraphSection config) {
        this.config = config;
    }

    /** @param keys package key of every graph file */
    public Result group(Map<FileInfo, Key> keys) {
        Map<FileInfo, List<String>> relative = new LinkedHashMap<>();
        Map<FileInfo, Kind> kinds = new HashMap<>();
        List<String> prefixes = new ArrayList<>();
        // Step 1, per kind: namespaces and directories have unrelated prefixes.
        for (Kind kind : Kind.values()) {
            List<List<String>> ofKind = new ArrayList<>();
            keys.values().stream().filter(k -> k.kind == kind).forEach(k -> ofKind.add(k.segments));
            if (ofKind.isEmpty()) {
                continue;
            }
            int prefixLength = commonPrefixLength(ofKind);
            if (prefixLength > 0) {
                prefixes.add(String.join(kind.separator, ofKind.get(0).subList(0, prefixLength)));
            }
            keys.forEach((file, key) -> {
                if (key.kind == kind) {
                    relative.put(file, key.segments.subList(prefixLength, key.segments.size()));
                    kinds.put(file, kind);
                }
            });
        }

        // Step 2: smallest depth giving enough groups; one step back if that overshoots the maximum.
        int maxLength = relative.values().stream().mapToInt(List::size).max().orElse(0);
        int depth = 1;
        while (depth < maxLength && countGroups(relative, kinds, depth) < config.getMinPackages()) {
            depth++;
        }
        if (depth > 1 && countGroups(relative, kinds, depth) > config.getMaxPackages()) {
            depth--;
        }

        // Step 3: depth becomes per file, because only oversized groups go deeper.
        Map<FileInfo, Integer> depthOf = new HashMap<>();
        for (FileInfo file : relative.keySet()) {
            depthOf.put(file, depth);
        }
        splitOversizedGroups(relative, kinds, depthOf);

        Map<FileInfo, String> groupOf = new LinkedHashMap<>();
        relative.forEach((file, rel) -> groupOf.put(file, name(kinds.get(file), rel, depthOf.get(file))));
        return new Result(groupOf, depth, prefixes);
    }

    /**
     * Repeatedly splits the largest group one level deeper while it holds more than
     * {@code splitFraction} of all files and at least {@code splitMinFiles}. A group is marked
     * unsplittable when going deeper would not divide it (all files in one sub-directory) or
     * would push the total over {@code maxPackages}.
     */
    private void splitOversizedGroups(Map<FileInfo, List<String>> relative, Map<FileInfo, Kind> kinds,
                                      Map<FileInfo, Integer> depthOf) {
        int total = relative.size();
        Set<String> unsplittable = new HashSet<>();
        for (int round = 0; round < MAX_SPLIT_ROUNDS; round++) {
            Map<String, List<FileInfo>> groups = new HashMap<>();
            relative.forEach((file, rel) ->
                    groups.computeIfAbsent(name(kinds.get(file), rel, depthOf.get(file)), k -> new ArrayList<>()).add(file));

            String largest = null;
            for (Map.Entry<String, List<FileInfo>> e : groups.entrySet()) {
                if (unsplittable.contains(e.getKey())) continue;
                if (largest == null || e.getValue().size() > groups.get(largest).size()) {
                    largest = e.getKey();
                }
            }
            if (largest == null) return;
            List<FileInfo> members = groups.get(largest);
            if (members.size() < config.getSplitMinFiles() || members.size() <= config.getSplitFraction() * total) {
                return;
            }

            Set<String> children = new HashSet<>();
            for (FileInfo file : members) {
                int next = Math.min(relative.get(file).size(), depthOf.get(file) + 1);
                children.add(name(kinds.get(file), relative.get(file), next));
            }
            if (children.size() <= 1 || groups.size() - 1 + children.size() > config.getMaxPackages()) {
                unsplittable.add(largest);
                continue;
            }
            for (FileInfo file : members) {
                depthOf.put(file, Math.min(relative.get(file).size(), depthOf.get(file) + 1));
            }
        }
    }

    /** Number of distinct groups if every file is cut at {@code depth}. */
    private static int countGroups(Map<FileInfo, List<String>> relative, Map<FileInfo, Kind> kinds, int depth) {
        Set<String> names = new HashSet<>();
        relative.forEach((file, rel) -> names.add(name(kinds.get(file), rel, depth)));
        return names.size();
    }

    /** Display name of a group: first {@code depth} segments, with "/" appended for directories. */
    private static String name(Kind kind, List<String> rel, int depth) {
        String joined = rel.isEmpty() ? ROOT : String.join(kind.separator, rel.subList(0, Math.min(depth, rel.size())));
        return kind == Kind.DIRECTORY ? joined + "/" : joined;
    }

    /**
     * Length of the prefix shared by all keys. If every key is identical the last segment is
     * kept so the single group still has a meaningful name.
     */
    static int commonPrefixLength(List<List<String>> keys) {
        List<String> first = keys.get(0);
        int length = first.size();
        boolean allIdentical = true;
        for (List<String> key : keys) {
            int i = 0;
            while (i < length && i < key.size() && key.get(i).equals(first.get(i))) i++;
            length = i;
            if (key.size() != first.size()) allIdentical = false;
        }
        if (allIdentical && length == first.size() && length > 0) {
            length--;
        }
        return length;
    }

    /** Splits a namespace or path into non-empty segments. */
    static List<String> segments(String value, String separatorRegex) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (String s : value.split(separatorRegex)) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
