package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between Swift files.
 *
 * <p>Swift has no file-level imports: every file of a module sees every type of that module.
 * So the types each file declares are indexed, and a file depends on the files declaring the
 * types it mentions. A type name declared in more than {@link #MAX_DECLARERS} files is too
 * ambiguous to say which file is meant and is ignored. {@code extension Foo} is not a
 * declaration of {@code Foo}, so it is not indexed.
 */
final class SwiftDependencyExtractor implements DependencyExtractor {
    static final int MAX_DECLARERS = 2;
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|struct|enum|protocol|actor|typealias)\\s+([A-Z]\\w*)");

    private final Map<FileInfo, String> code = new HashMap<>();
    private final Map<String, List<FileInfo>> declarers = new HashMap<>();

    SwiftDependencyExtractor(SourceSet sources, Collection<FileInfo> files) {
        for (FileInfo file : files) {
            String cleaned = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.SWIFT);
            code.put(file, cleaned);
            Matcher m = TYPE.matcher(cleaned);
            while (m.find()) {
                List<FileInfo> list = declarers.computeIfAbsent(m.group(1), k -> new ArrayList<>());
                if (!list.contains(file)) list.add(file);
            }
        }
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        Map<String, int[]> tokens = SourceScanner.countIdentifiers(SourceScanner.blankLinesStartingWith(code.get(file), "import"));
        DependencyCollector deps = new DependencyCollector(file);
        for (Map.Entry<String, List<FileInfo>> type : declarers.entrySet()) {
            List<FileInfo> files = type.getValue();
            if (files.size() > MAX_DECLARERS || files.contains(file)) continue;
            int uses = SourceScanner.count(tokens, type.getKey());
            for (FileInfo target : files) {
                deps.add(target, uses, SourceScanner.firstLine(tokens, type.getKey()));
            }
        }
        return deps.toList();
    }
}
