package com.deepdoc.docs;

import com.deepdoc.model.FileInfo;

/**
 * File-name helpers shared by the page writers: a safe Markdown filename for a package,
 * and a one-line role guessed from a file stem when Week 2 did not outline that file.
 */
final class DocNames {
    private DocNames() {
    }

    /**
     * {@code order.service} stays readable; slashes and spaces become dashes so the path is
     * a single path segment under {@code packages/}.
     */
    static String packageFileName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return "package.md";
        }
        StringBuilder slug = new StringBuilder();
        for (int i = 0; i < packageName.length(); i++) {
            char c = packageName.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-') {
                slug.append(c);
            } else {
                slug.append('-');
            }
        }
        String name = slug.toString();
        while (name.contains("--")) {
            name = name.replace("--", "-");
        }
        if (name.startsWith("-") || name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.endsWith("-") || name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.isEmpty()) {
            name = "package";
        }
        return name + ".md";
    }

    static String packageDocPath(String packageName) {
        return "packages/" + packageFileName(packageName);
    }

    static String fileStem(FileInfo file) {
        String name = file.getFilePath().getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /** {@code OrderValidator} → "Order validator." when there is no outline to quote. */
    static String listedRole(FileInfo file) {
        String stem = fileStem(file);
        if (stem.isEmpty()) {
            return "Source file.";
        }
        StringBuilder words = new StringBuilder();
        for (int i = 0; i < stem.length(); i++) {
            char c = stem.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) {
                words.append(' ');
            }
            words.append(i == 0 ? c : Character.toLowerCase(c));
        }
        String sentence = words.toString().trim();
        if (sentence.isEmpty()) {
            return "Source file.";
        }
        if (!sentence.endsWith(".")) {
            sentence = sentence + ".";
        }
        return sentence;
    }
}
