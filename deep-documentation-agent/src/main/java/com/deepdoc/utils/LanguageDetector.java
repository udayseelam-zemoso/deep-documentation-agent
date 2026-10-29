package com.deepdoc.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Maps a file to a language from {@code languages.yml}.
 *
 * <p>Order of checks: exact file name ({@code Dockerfile}), then the longest matching extension
 * ({@code foo.test.ts} tries {@code .test.ts} and then {@code .ts}), then - only for files with
 * no extension - the interpreter on the {@code #!} line.
 */
public class LanguageDetector {
    private static final int SHEBANG_BYTES = 256;

    private final LanguageCatalog catalog;

    public LanguageDetector() {
        this(LanguageCatalog.getDefault());
    }

    LanguageDetector(LanguageCatalog catalog) {
        this.catalog = catalog;
    }

    /** Language name, or null if the file is not described by the catalog. */
    public String detectLanguage(Path filePath) {
        LanguageDefinition language = detect(filePath);
        return language == null ? null : language.getName();
    }

    public LanguageDefinition detect(Path filePath) {
        Path namePath = filePath.getFileName();
        if (namePath == null) {
            return null;
        }
        String fileName = namePath.toString();

        LanguageDefinition byName = catalog.byFilename(fileName);
        if (byName != null) {
            return byName;
        }

        // Start after a leading dot so ".bashrc" is treated as having no extension.
        int dot = fileName.indexOf('.', 1);
        while (dot >= 0) {
            LanguageDefinition byExtension = catalog.byExtension(fileName.substring(dot));
            if (byExtension != null) {
                return byExtension;
            }
            dot = fileName.indexOf('.', dot + 1);
        }

        if (fileName.indexOf('.', 1) < 0) {
            String interpreter = shebangInterpreter(filePath);
            if (interpreter != null) {
                return catalog.byInterpreter(interpreter);
            }
        }
        return null;
    }

    /** Type of a language name returned by {@link #detectLanguage}; null if unknown. */
    public LanguageType typeOf(String languageName) {
        LanguageDefinition language = languageName == null ? null : catalog.byName(languageName);
        return language == null ? null : language.languageType();
    }

    public boolean isProgrammingLanguage(String languageName) {
        return typeOf(languageName) == LanguageType.PROGRAMMING;
    }

    /** True for any file the catalog knows, code or not. */
    public boolean isSourceCodeFile(Path filePath) {
        return detect(filePath) != null;
    }

    /**
     * Reads the interpreter from a {@code #!} line, normalized so the catalog can match it:
     * {@code #!/usr/bin/env -S python3.11 -u} gives {@code python}, {@code #!/bin/bash} gives {@code bash}.
     */
    static String shebangInterpreter(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        byte[] head = new byte[SHEBANG_BYTES];
        int read;
        try (InputStream in = Files.newInputStream(file)) {
            read = in.read(head);
        } catch (IOException e) {
            return null;
        }
        if (read < 2 || head[0] != '#' || head[1] != '!') {
            return null;
        }
        String line = new String(head, 2, read - 2, StandardCharsets.UTF_8);
        int newline = line.indexOf('\n');
        if (newline >= 0) {
            line = line.substring(0, newline);
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        String program = basename(parts[0]);
        if (program.equals("env")) {
            program = null;
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].startsWith("-") && !parts[i].contains("=")) {
                    program = basename(parts[i]);
                    break;
                }
            }
            if (program == null) {
                return null;
            }
        }
        // python3.11 -> python, but keep names like "pwsh" or "ts-node" intact
        return program.replaceAll("[0-9.]+$", "");
    }

    private static String basename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }
}
