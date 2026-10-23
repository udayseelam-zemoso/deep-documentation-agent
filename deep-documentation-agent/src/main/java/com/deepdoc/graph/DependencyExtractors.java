package com.deepdoc.graph;

import com.deepdoc.config.GraphSection;
import com.deepdoc.model.FileInfo;

import java.util.Collection;

/** Creates the extractor for a language family. */
final class DependencyExtractors {
    private DependencyExtractors() {
    }

    static DependencyExtractor create(LanguageFamily family, SourceSet sources, Collection<FileInfo> files,
                                      GraphSection config) {
        switch (family) {
            case JVM:
                return new JvmDependencyExtractor(sources, files);
            case JAVASCRIPT:
                return new JsDependencyExtractor(sources, files, config.getJsAliases());
            default:
                throw new IllegalArgumentException("No extractor for " + family);
        }
    }
}
