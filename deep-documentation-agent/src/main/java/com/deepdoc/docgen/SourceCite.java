package com.deepdoc.docgen;

import com.deepdoc.model.SourceReference;

import java.nio.file.Path;

/**
 * Turns a {@link SourceReference} into a Markdown link a reader can click.
 *
 * <p>Docs live under the output folder (often {@code docs/} or {@code docs/packages/}), while
 * the cited file lives in the repository. The link is therefore relative to the Markdown file
 * being written, with a {@code #L} fragment for the line. GitHub, GitLab and most previewers
 * understand that fragment.
 */
public final class SourceCite {
    private SourceCite() {
    }

    /** Repository-relative path with forward slashes, for display. */
    public static String displayPath(Path repositoryRoot, Path file) {
        if (repositoryRoot == null || file == null) {
            return file == null ? "" : file.toString().replace('\\', '/');
        }
        try {
            return repositoryRoot.normalize().relativize(file.normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return file.getFileName() == null ? file.toString() : file.getFileName().toString();
        }
    }

    public static String fileLink(Path fromDoc, Path source, String label) {
        return "[" + linkText(label) + "](" + relativeUrl(fromDoc, source) + ")";
    }

    public static String lineLink(Path fromDoc, Path source, int line, String label) {
        String url = relativeUrl(fromDoc, source);
        if (line > 0) {
            url = url + "#L" + line;
        }
        return "[" + linkText(label) + "](" + url + ")";
    }

    /**
     * {@code OrderService.java:3} linking at that line, plus an optional inline snippet of the
     * cited source so the claim is readable without leaving the page.
     */
    public static String cite(Path fromDoc, Path repositoryRoot, SourceReference ref) {
        if (ref == null || ref.getFile() == null) {
            return "";
        }
        String label = ref.getFile().getFileName() + ":" + ref.getStartLine();
        String link = lineLink(fromDoc, ref.getFile(), ref.getStartLine(), label);
        if (ref.getSnippet() == null || ref.getSnippet().trim().isEmpty()) {
            return link;
        }
        return link + " — `" + Markdown.code(ref.getSnippet()) + "`";
    }

    static String relativeUrl(Path fromDoc, Path target) {
        if (fromDoc == null || target == null) {
            return "";
        }
        Path fromDir = fromDoc.getParent() == null ? fromDoc : fromDoc.getParent();
        try {
            return fromDir.normalize().relativize(target.normalize()).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return target.toString().replace('\\', '/');
        }
    }

    private static String linkText(String label) {
        if (label == null || label.isEmpty()) {
            return "source";
        }
        return label.replace("[", "\\[").replace("]", "\\]");
    }
}
