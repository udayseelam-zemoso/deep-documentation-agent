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
 * Groups files into packages at an automatically chosen depth.
 *
 * <ol>
 *   <li>Strip the prefix shared by every file of a kind ({@code com.acme.shop}, {@code src}).</li>
 *   <li>Take the first {@code d} remaining segments as the group, growing {@code d} from 1 until
 *       there are at least {@code minPackages} groups (backing off if that overshoots {@code maxPackages}).</li>
 *   <li>Split any group that still holds more than {@code splitFraction} of all files one level deeper.</li>
 * </ol>
 * Java files are keyed by their package declaration, everything else by directory.
 * Directory groups are displayed with a trailing {@code /} so they never collide with Java packages.
 */
public final class PackageGrouper {
    public static final String ROOT = "(root)";

    public enum Kind {
        JAVA_PACKAGE("."),
        DIRECTORY("/");

        final String separator;

        Kind(String separator) {
            this.separator = separator;
        }
    }

    public static final class Key {
        final Kind kind;
        final List<String> segments;

        public Key(Kind kind, List<String> segments) {
            this.kind = kind;
            this.segments = segments;
        }
    }

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

        public int getDepth() {
            return depth;
        }

        public List<String> getStrippedPrefixes() {
            return strippedPrefixes;
        }
    }

    private static final int MAX_SPLIT_ROUNDS = 20;

    private final GraphSection config;

    public PackageGrouper(GraphSection config) {
        this.config = config;
    }

    public Result group(Map<FileInfo, Key> keys) {
        Map<FileInfo, List<String>> relative = new LinkedHashMap<>();
        Map<FileInfo, Kind> kinds = new HashMap<>();
        List<String> prefixes = new ArrayList<>();
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

        int maxLength = relative.values().stream().mapToInt(List::size).max().orElse(0);
        int depth = 1;
        while (depth < maxLength && countGroups(relative, kinds, depth) < config.getMinPackages()) {
            depth++;
        }
        if (depth > 1 && countGroups(relative, kinds, depth) > config.getMaxPackages()) {
            depth--;
        }

        Map<FileInfo, Integer> depthOf = new HashMap<>();
        for (FileInfo file : relative.keySet()) {
            depthOf.put(file, depth);
        }
        splitOversizedGroups(relative, kinds, depthOf);

        Map<FileInfo, String> groupOf = new LinkedHashMap<>();
        relative.forEach((file, rel) -> groupOf.put(file, name(kinds.get(file), rel, depthOf.get(file))));
        return new Result(groupOf, depth, prefixes);
    }

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

    private static int countGroups(Map<FileInfo, List<String>> relative, Map<FileInfo, Kind> kinds, int depth) {
        Set<String> names = new HashSet<>();
        relative.forEach((file, rel) -> names.add(name(kinds.get(file), rel, depth)));
        return names.size();
    }

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
