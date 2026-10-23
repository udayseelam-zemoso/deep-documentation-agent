package com.deepdoc.graph;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recognises test sources by conventional directory and file names. Tests are left out of the
 * graph: they depend on everything they test, which would inflate the rank of tested code
 * without saying anything about how the application itself is wired.
 */
public final class TestFileDetector {
    /** A file anywhere below one of these directories is a test (Ruby/RSpec uses {@code spec}). */
    private static final Set<String> TEST_DIRECTORIES = new HashSet<>(Arrays.asList(
            "test", "tests", "__tests__", "__mocks__", "e2e", "spec", "testdata"));

    /** File-name conventions, one alternative per ecosystem. */
    private static final Pattern TEST_FILE = Pattern.compile(String.join("|",
            ".*(Test|Tests|IT|TestCase|Spec)\\.(java|kt|kts|scala|groovy)",  // JUnit, Kotest, ScalaTest, Spock
            ".*\\.(test|spec)\\.(js|jsx|ts|tsx|mjs|cjs)",                    // Jest, Vitest, Mocha
            "test_.*\\.py|.*_test\\.py|conftest\\.py",                       // pytest
            ".*_test\\.go",                                                  // go test
            ".*(Test|Tests)\\.(cs|php|swift)",                               // xUnit/NUnit, PHPUnit, XCTest
            ".*_(spec|test)\\.rb",                                           // RSpec, Minitest
            ".*_(test|unittest)\\.(c|cc|cpp|cxx)|test_.*\\.(c|cc|cpp|cxx)",  // GoogleTest, Unity
            ".*\\.bats"));                                                   // shell tests

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
