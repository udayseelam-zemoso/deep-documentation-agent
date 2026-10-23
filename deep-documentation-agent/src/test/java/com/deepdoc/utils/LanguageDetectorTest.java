package com.deepdoc.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class LanguageDetectorTest {
    
    private final LanguageDetector detector = new LanguageDetector();

    @Test
    void shouldDetectJavaFiles() {
        Path javaFile = Paths.get("src/main/java/Example.java");
        assertThat(detector.detectLanguage(javaFile)).isEqualTo("Java");
    }

    @Test
    void shouldDetectJavaScriptFiles() {
        Path jsFile = Paths.get("src/components/Example.js");
        assertThat(detector.detectLanguage(jsFile)).isEqualTo("JavaScript");
    }

    @Test
    void shouldDetectTypeScriptFiles() {
        Path tsFile = Paths.get("src/services/api.ts");
        assertThat(detector.detectLanguage(tsFile)).isEqualTo("TypeScript");
        
        Path tsxFile = Paths.get("src/components/App.tsx");
        assertThat(detector.detectLanguage(tsxFile)).isEqualTo("TypeScript");
    }

    @Test
    void shouldDetectPythonFiles() {
        Path pyFile = Paths.get("scripts/deploy.py");
        assertThat(detector.detectLanguage(pyFile)).isEqualTo("Python");
    }

    @Test
    void shouldReturnNullForUnknownExtensions() {
        Path unknownFile = Paths.get("data/file.xyz");
        assertThat(detector.detectLanguage(unknownFile)).isNull();
    }

    @Test
    void shouldDetectMarkdownFiles() {
        Path mdFile = Paths.get("design/README.md");
        assertThat(detector.detectLanguage(mdFile)).isEqualTo("Markdown");
    }

    @Test
    void shouldDetectYamlFiles() {
        Path yamlFile = Paths.get("config/app.yaml");
        assertThat(detector.detectLanguage(yamlFile)).isEqualTo("YAML");
        
        Path ymlFile = Paths.get("config/app.yml");
        assertThat(detector.detectLanguage(ymlFile)).isEqualTo("YAML");
    }

    @Test
    void shouldMatchFileNamesBeforeExtensions() {
        assertThat(detector.detectLanguage(Paths.get("Dockerfile"))).isEqualTo("Dockerfile");
        assertThat(detector.detectLanguage(Paths.get("deploy/Dockerfile.prod"))).isEqualTo("Dockerfile");
        assertThat(detector.detectLanguage(Paths.get("CMakeLists.txt"))).isEqualTo("CMake");
        assertThat(detector.detectLanguage(Paths.get("Jenkinsfile"))).isEqualTo("Groovy");
        assertThat(detector.detectLanguage(Paths.get("notes.txt"))).isNull();
    }

    @Test
    void shouldUseLastMatchingExtensionCaseInsensitively() {
        assertThat(detector.detectLanguage(Paths.get("src/app.test.ts"))).isEqualTo("TypeScript");
        assertThat(detector.detectLanguage(Paths.get("analysis/model.R"))).isEqualTo("R");
        assertThat(detector.detectLanguage(Paths.get("lib/app.ex"))).isEqualTo("Elixir");
        assertThat(detector.detectLanguage(Paths.get("src/App.vue"))).isEqualTo("Vue");
        assertThat(detector.detectLanguage(Paths.get("include/util.h"))).isEqualTo("C/C++");
    }

    @Test
    void shouldDetectExtensionlessScriptsFromShebang(@TempDir Path dir) throws IOException {
        Path python = dir.resolve("manage");
        Files.write(python, "#!/usr/bin/env python3.11\nprint('hi')\n".getBytes());
        Path bash = dir.resolve("deploy");
        Files.write(bash, "#!/bin/bash\necho hi\n".getBytes());
        Path node = dir.resolve("cli");
        Files.write(node, "#!/usr/bin/env -S node --no-warnings\n".getBytes());
        Path plain = dir.resolve("LICENSE");
        Files.write(plain, "MIT License\n".getBytes());

        assertThat(detector.detectLanguage(python)).isEqualTo("Python");
        assertThat(detector.detectLanguage(bash)).isEqualTo("Shell");
        assertThat(detector.detectLanguage(node)).isEqualTo("JavaScript");
        assertThat(detector.detectLanguage(plain)).isNull();
    }

    @Test
    void shouldClassifyLanguagesByType() {
        assertThat(detector.isProgrammingLanguage("Go")).isTrue();
        assertThat(detector.isProgrammingLanguage("YAML")).isFalse();
        assertThat(detector.typeOf("Markdown")).isEqualTo(LanguageType.PROSE);
        assertThat(detector.typeOf("Dockerfile")).isEqualTo(LanguageType.CONFIG);
        assertThat(detector.typeOf("CSS")).isEqualTo(LanguageType.MARKUP);
        assertThat(detector.typeOf("SQL")).isEqualTo(LanguageType.DATA);
    }

    @Test
    void shouldIdentifySourceCodeFiles() {
        assertThat(detector.isSourceCodeFile(Paths.get("Example.java"))).isTrue();
        assertThat(detector.isSourceCodeFile(Paths.get("app.js"))).isTrue();
        assertThat(detector.isSourceCodeFile(Paths.get("data.json"))).isTrue();
        assertThat(detector.isSourceCodeFile(Paths.get("unknown.xyz"))).isFalse();
    }
}
