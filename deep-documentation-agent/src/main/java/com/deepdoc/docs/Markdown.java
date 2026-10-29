package com.deepdoc.docs;

import java.util.List;

/**
 * Small Markdown builder. Pages append headings, paragraphs, lists and tables, then
 * {@link #build()} the file body. Tables escape pipes so a type name like {@code A|B} cannot
 * break the grid.
 */
final class Markdown {
    private final StringBuilder out = new StringBuilder();

    Markdown heading(int level, String text) {
        for (int i = 0; i < level; i++) {
            out.append('#');
        }
        out.append(' ').append(text == null ? "" : text).append("\n\n");
        return this;
    }

    Markdown paragraph(String text) {
        if (text == null || text.isEmpty()) {
            return this;
        }
        out.append(text).append("\n\n");
        return this;
    }

    Markdown bullet(String text) {
        out.append("- ").append(text == null ? "" : text).append('\n');
        return this;
    }

    Markdown line(String text) {
        out.append(text == null ? "" : text).append('\n');
        return this;
    }

    Markdown blank() {
        if (out.length() == 0 || out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        out.append('\n');
        return this;
    }

    Markdown mermaid(String body) {
        if (body == null || body.trim().isEmpty()) {
            return this;
        }
        out.append("```mermaid\n").append(body);
        if (!body.endsWith("\n")) {
            out.append('\n');
        }
        out.append("```\n\n");
        return this;
    }

    Markdown table(List<String> headers, List<List<String>> rows) {
        if (headers == null || headers.isEmpty()) {
            return this;
        }
        out.append('|');
        for (String header : headers) {
            out.append(' ').append(cell(header)).append(' ').append('|');
        }
        out.append('\n').append('|');
        for (int i = 0; i < headers.size(); i++) {
            out.append(" --- |");
        }
        out.append('\n');
        if (rows != null) {
            for (List<String> row : rows) {
                out.append('|');
                for (int i = 0; i < headers.size(); i++) {
                    String value = row != null && i < row.size() ? row.get(i) : "";
                    out.append(' ').append(cell(value)).append(' ').append('|');
                }
                out.append('\n');
            }
        }
        out.append('\n');
        return this;
    }

    String build() {
        return out.toString();
    }

    /** Pipe and newline would break a table cell; keep the cell on one line. */
    static String cell(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|").replace("\n", " ").trim();
    }

    /** Backticks inside inline code are replaced so the span stays closed. */
    static String code(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('`', '\'').replace("\n", " ").trim();
    }
}
