package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.util.List;

/**
 * Finds the files a source file depends on, for one {@link LanguageFamily}.
 *
 * <p>An extractor is created once per build with all files of its family, so it can index
 * declarations up front, and is then asked for each file's dependencies. Implementations
 * are lexical (regular expressions over comment-stripped source), not full parsers: they aim
 * to find the dependencies a reader would see, cheaply, across very large repositories.
 */
interface DependencyExtractor {

    List<RawDependency> extract(FileInfo file);

    /**
     * Declared namespace (Java/Kotlin package, C# or PHP namespace) used to group the file into
     * a package. Null means "group by directory", which is right for most languages.
     */
    default String namespaceOf(FileInfo file) {
        return null;
    }
}
