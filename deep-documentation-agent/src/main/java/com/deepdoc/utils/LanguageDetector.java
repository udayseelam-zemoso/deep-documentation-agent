package com.deepdoc.utils;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class LanguageDetector {
    private static final Map<String, String> EXTENSION_TO_LANGUAGE = new HashMap<>();

    static {
        EXTENSION_TO_LANGUAGE.put(".java", "Java");
        EXTENSION_TO_LANGUAGE.put(".js", "JavaScript");
        EXTENSION_TO_LANGUAGE.put(".jsx", "JavaScript");
        EXTENSION_TO_LANGUAGE.put(".mjs", "JavaScript");
        EXTENSION_TO_LANGUAGE.put(".cjs", "JavaScript");
        EXTENSION_TO_LANGUAGE.put(".ts", "TypeScript");
        EXTENSION_TO_LANGUAGE.put(".tsx", "TypeScript");
        EXTENSION_TO_LANGUAGE.put(".py", "Python");
        EXTENSION_TO_LANGUAGE.put(".go", "Go");
        EXTENSION_TO_LANGUAGE.put(".rs", "Rust");
        EXTENSION_TO_LANGUAGE.put(".cpp", "C++");
        EXTENSION_TO_LANGUAGE.put(".cc", "C++");
        EXTENSION_TO_LANGUAGE.put(".cxx", "C++");
        EXTENSION_TO_LANGUAGE.put(".c", "C");
        EXTENSION_TO_LANGUAGE.put(".h", "C/C++");
        EXTENSION_TO_LANGUAGE.put(".hpp", "C++");
        EXTENSION_TO_LANGUAGE.put(".cs", "C#");
        EXTENSION_TO_LANGUAGE.put(".rb", "Ruby");
        EXTENSION_TO_LANGUAGE.put(".php", "PHP");
        EXTENSION_TO_LANGUAGE.put(".swift", "Swift");
        EXTENSION_TO_LANGUAGE.put(".kt", "Kotlin");
        EXTENSION_TO_LANGUAGE.put(".kts", "Kotlin");
        EXTENSION_TO_LANGUAGE.put(".scala", "Scala");
        EXTENSION_TO_LANGUAGE.put(".sql", "SQL");
        EXTENSION_TO_LANGUAGE.put(".sh", "Shell");
        EXTENSION_TO_LANGUAGE.put(".bash", "Shell");
        EXTENSION_TO_LANGUAGE.put(".xml", "XML");
        EXTENSION_TO_LANGUAGE.put(".json", "JSON");
        EXTENSION_TO_LANGUAGE.put(".yaml", "YAML");
        EXTENSION_TO_LANGUAGE.put(".yml", "YAML");
        EXTENSION_TO_LANGUAGE.put(".md", "Markdown");
        EXTENSION_TO_LANGUAGE.put(".html", "HTML");
        EXTENSION_TO_LANGUAGE.put(".css", "CSS");
        EXTENSION_TO_LANGUAGE.put(".scss", "SCSS");
        EXTENSION_TO_LANGUAGE.put(".less", "LESS");
    }

    public String detectLanguage(Path filePath) {
        String fileName = filePath.getFileName().toString();
        
        for (Map.Entry<String, String> entry : EXTENSION_TO_LANGUAGE.entrySet()) {
            if (fileName.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }

    public boolean isSourceCodeFile(Path filePath) {
        return detectLanguage(filePath) != null;
    }
}
