package com.deepdoc.ranker;

import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.LanguageFamily;
import com.deepdoc.graph.SourceScanner;
import com.deepdoc.model.FileInfo;
import com.deepdoc.utils.SourceFiles;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds files where execution or requests enter the system: main functions, application
 * classes, HTTP controllers and routes, CLI commands, message listeners and UI roots.
 * Entry points get a larger PageRank prior and always receive at least a summary.
 *
 * <p>Patterns are matched after comments and string literals are blanked, so a commented-out
 * {@code @RestController} does not count.
 */
public final class EntryPointDetector {
    private static final Map<LanguageFamily, Pattern> PATTERNS = new EnumMap<>(LanguageFamily.class);

    static {
        PATTERNS.put(LanguageFamily.JVM, Pattern.compile(
                "@(SpringBootApplication|RestController|Controller|WebServlet|Command|KafkaListener|RabbitListener"
                        + "|JmsListener|Path)\\b"
                        + "|public\\s+static\\s+void\\s+main\\s*\\("     // Java
                        + "|^fun\\s+main\\s*\\("                          // Kotlin
                        + "|\\bdef\\s+main\\s*\\(|\\bextends\\s+App\\b",   // Scala
                Pattern.MULTILINE));
        PATTERNS.put(LanguageFamily.JAVASCRIPT, Pattern.compile(
                "\\.listen\\s*\\(|\\bcreateServer\\s*\\(|\\bcreateRoot\\s*\\(|\\bReactDOM\\.render\\s*\\("
                        + "|\\bcreateApp\\s*\\("                          // Vue
                        + "|\\b(?:router|app)\\.(?:get|post|put|patch|delete)\\s*\\("));
    }

    private EntryPointDetector() {
    }

    public static Set<FileInfo> detect(DependencyGraph graph) {
        Set<FileInfo> entries = new HashSet<>();
        for (FileInfo file : graph.getFileGraph().vertexSet()) {
            String source = SourceFiles.read(file.getFilePath());
            if (source != null && isEntryPoint(file.getLanguage(), source)) {
                entries.add(file);
            }
        }
        return entries;
    }

    static boolean isEntryPoint(String language, String source) {
        LanguageFamily family = LanguageFamily.of(language);
        Pattern pattern = family == null ? null : PATTERNS.get(family);
        if (pattern == null) {
            return false;
        }
        String code = SourceScanner.blankCommentsAndLiterals(source, family.syntax());
        return pattern.matcher(code).find();
    }
}
