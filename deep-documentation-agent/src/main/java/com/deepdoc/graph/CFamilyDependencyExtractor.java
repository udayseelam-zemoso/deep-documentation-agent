package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between C, C++ and Objective-C files from {@code #include} and {@code #import}.
 *
 * <p>{@code #include "x.h"} is looked up next to the including file first, as compilers do.
 * Otherwise - and for {@code <x.h>} - the include directories are unknown (they live in the
 * build system), so the include is matched as a path suffix anywhere in the repository,
 * preferring the closest file: {@code <acme/util/money.h>} finds {@code include/acme/util/money.h}.
 * System headers ({@code <vector>}, {@code <stdio.h>}) have no file in the repository and stay
 * unresolved. Each directive counts as one reference.
 */
final class CFamilyDependencyExtractor implements DependencyExtractor {
    private static final Pattern INCLUDE = Pattern.compile("(?m)^\\s*#\\s*(?:include|import)\\s*([<\"])([^>\"\\n]+)[>\"]");

    private final SourceSet sources;

    CFamilyDependencyExtractor(SourceSet sources) {
        this.sources = sources;
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String code = SourceScanner.blankComments(sources.source(file), SourceScanner.Syntax.C_LIKE);
        LineIndex lines = new LineIndex(code);
        DependencyCollector deps = new DependencyCollector(file);
        Matcher m = INCLUDE.matcher(code);
        while (m.find()) {
            String header = m.group(2).trim();
            FileInfo target = null;
            if (m.group(1).equals("\"")) {
                Path local = SourceSet.normalize(file.getFilePath()).getParent().resolve(header).normalize();
                target = sources.at(local);
            }
            if (target == null) {
                target = sources.findBySuffix(header, file, f -> LanguageFamily.of(f.getLanguage()) == LanguageFamily.C_FAMILY);
            }
            deps.add(target, 1, lines.lineOf(m.start()));
        }
        return deps.toList();
    }
}
