package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between shell scripts: {@code source x.sh} and {@code . x.sh}.
 *
 * <p>Scripts usually locate their helpers through the script directory, e.g.
 * {@code source "$(dirname "$0")/lib/log.sh"} or {@code . "${SCRIPT_DIR}/lib/log.sh"}. A leading
 * {@code $(...)}, {@code ${...}} or {@code $VAR} is therefore read as "the script's directory".
 * Paths are tried relative to the script, then to the repository root.
 */
final class ShellDependencyExtractor implements DependencyExtractor {
    /**
     * Group 1 is an optional directory prefix ({@code $(...)/}, {@code ${...}/}, {@code $VAR/}); it
     * is matched separately because {@code $(dirname "$0")} contains quotes. Group 2 is the path.
     */
    private static final Pattern SOURCE = Pattern.compile(
            "(?m)^\\s*(?:source|\\.)\\s+[\"']?(\\$\\([^)]*\\)/|\\$\\{[^}]*\\}/|\\$\\w+/)?([^\"'\\s;|&]+)");

    private final SourceSet sources;

    ShellDependencyExtractor(SourceSet sources) {
        this.sources = sources;
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String code = SourceScanner.blankComments(sources.source(file), SourceScanner.Syntax.SHELL);
        LineIndex lines = new LineIndex(code);
        DependencyCollector deps = new DependencyCollector(file);
        Path dir = SourceSet.normalize(file.getFilePath()).getParent();
        Matcher m = SOURCE.matcher(code);
        while (m.find()) {
            String path = m.group(2);
            if (path.contains("$")) continue;
            FileInfo target = sources.at(dir.resolve(path));
            if (target == null) target = sources.at(sources.root().resolve(path.replaceFirst("^/", "")));
            deps.add(target, 1, lines.lineOf(m.start()));
        }
        return deps.toList();
    }
}
