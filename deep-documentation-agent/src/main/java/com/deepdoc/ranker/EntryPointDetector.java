package com.deepdoc.ranker;

import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.JavaSourceScanner;
import com.deepdoc.model.FileInfo;
import com.deepdoc.utils.SourceFiles;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Finds files where execution or requests enter the system: main methods, application classes,
 * controllers, CLI commands, message listeners, servers and UI roots.
 */
public final class EntryPointDetector {
    private static final Pattern JAVA_ENTRY = Pattern.compile(
            "@(SpringBootApplication|RestController|Controller|WebServlet|Command|KafkaListener|RabbitListener"
                    + "|JmsListener|Path)\\b"
                    + "|public\\s+static\\s+void\\s+main\\s*\\(");
    private static final Pattern JS_ENTRY = Pattern.compile(
            "\\.listen\\s*\\(|\\bcreateServer\\s*\\(|\\bcreateRoot\\s*\\(|\\bReactDOM\\.render\\s*\\("
                    + "|\\b(?:router|app)\\.(?:get|post|put|patch|delete)\\s*\\(");

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
        String code = JavaSourceScanner.blankCommentsAndLiterals(source);
        Pattern pattern = "Java".equals(language) ? JAVA_ENTRY : JS_ENTRY;
        return pattern.matcher(code).find();
    }
}
