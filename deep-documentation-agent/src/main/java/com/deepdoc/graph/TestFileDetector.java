package com.deepdoc.graph;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Recognises test sources by conventional directory and file names. */
public final class TestFileDetector {
    private static final Set<String> TEST_DIRECTORIES =
            new HashSet<>(Arrays.asList("test", "tests", "__tests__", "__mocks__", "e2e"));
    private static final Pattern TEST_FILE = Pattern.compile(
            ".*(Test|Tests|IT|TestCase)\\.java$|.*\\.(test|spec)\\.(js|jsx|ts|tsx|mjs|cjs)$");

    private TestFileDetector() {
    }

    /** @param relativePath path relative to the repository root */
    public static boolean isTest(Path relativePath) {
        for (Path segment : relativePath) {
            if (TEST_DIRECTORIES.contains(segment.toString())) {
                return true;
            }
        }
        Path fileName = relativePath.getFileName();
        return fileName != null && TEST_FILE.matcher(fileName.toString()).matches();
    }
}
