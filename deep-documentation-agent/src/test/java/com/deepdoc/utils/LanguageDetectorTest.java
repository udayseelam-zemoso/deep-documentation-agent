package com.deepdoc.utils;

import org.junit.jupiter.api.Test;
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
        Path mdFile = Paths.get("docs/README.md");
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
    void shouldIdentifySourceCodeFiles() {
        assertThat(detector.isSourceCodeFile(Paths.get("Example.java"))).isTrue();
        assertThat(detector.isSourceCodeFile(Paths.get("app.js"))).isTrue();
        assertThat(detector.isSourceCodeFile(Paths.get("data.json"))).isTrue();
        assertThat(detector.isSourceCodeFile(Paths.get("unknown.xyz"))).isFalse();
    }
}
