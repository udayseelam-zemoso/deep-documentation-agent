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
            case PYTHON:
                return new PythonDependencyExtractor(sources, files);
            case GO:
                return new GoDependencyExtractor(sources, files);
            case CSHARP:
                return new CSharpDependencyExtractor(sources, files);
            case C_FAMILY:
                return new CFamilyDependencyExtractor(sources);
            case RUST:
                return new RustDependencyExtractor(sources, files);
            case RUBY:
                return new RubyDependencyExtractor(sources);
            case PHP:
                return new PhpDependencyExtractor(sources, files);
            case SWIFT:
                return new SwiftDependencyExtractor(sources, files);
            case SHELL:
                return new ShellDependencyExtractor(sources);
            default:
                throw new IllegalArgumentException("No extractor for " + family);
        }
    }
}
