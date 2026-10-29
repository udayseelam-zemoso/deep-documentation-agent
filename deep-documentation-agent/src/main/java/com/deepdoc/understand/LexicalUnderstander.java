package com.deepdoc.understand;

import com.deepdoc.graph.LanguageFamily;
import com.deepdoc.graph.SourceScanner;
import com.deepdoc.model.FileInfo;
import com.deepdoc.ranker.DocLevel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outline for languages we do not run through a full parser. Comments and strings are blanked
 * first (same {@link SourceScanner} the graph uses), then declarations are picked up with a
 * small set of patterns per family.
 *
 * <p>This is what Week 2 uses for JS/TS, Python, Go, C# and the rest. A full ANTLR TypeScript
 * grammar is tens of thousands of generated lines and still needs a binder; the lexical outline
 * is enough for a responsibility sentence and a symbol list. Java goes through
 * {@link JavaAstUnderstander} instead.
 */
public final class LexicalUnderstander implements FileUnderstander {
    private static final Pattern JAVA_LIKE_TYPE = Pattern.compile(
            "(?m)^\\s*(?:public|protected|private|internal|export|abstract|final|open|data|sealed|static|partial)\\s+"
                    + "(?:public|protected|private|static|abstract|final|open|data|sealed|partial\\s+)*"
                    + "(class|interface|enum|record|struct|trait|object|annotation)\\s+(\\w+)");
    private static final Pattern JAVA_LIKE_TYPE_SHORT = Pattern.compile(
            "(?m)^\\s*(class|interface|enum|record|struct|trait|object)\\s+(\\w+)");
    private static final Pattern JAVA_LIKE_METHOD = Pattern.compile(
            "(?m)^\\s*(?:public|protected|private|internal|static|async|override|virtual|final|open|fun|def)?\\s*"
                    + "(?:fun|def|function)?\\s+(\\w+)\\s*\\(");
    private static final Pattern JS_CLASS = Pattern.compile("(?m)^\\s*(?:export\\s+(?:default\\s+)?)?class\\s+(\\w+)");
    private static final Pattern JS_FUNCTION = Pattern.compile(
            "(?m)^\\s*(?:export\\s+(?:default\\s+)?)?(?:async\\s+)?function\\s+(\\w+)\\s*\\(");
    private static final Pattern JS_CONST_FN = Pattern.compile(
            "(?m)^\\s*(?:export\\s+)?const\\s+(\\w+)\\s*=\\s*(?:async\\s+)?(?:\\([^)]*\\)|\\w+)\\s*=>");
    private static final Pattern PY_CLASS = Pattern.compile("(?m)^class\\s+(\\w+)");
    private static final Pattern PY_DEF = Pattern.compile("(?m)^(?:async\\s+)?def\\s+(\\w+)\\s*\\(");
    private static final Pattern GO_TYPE = Pattern.compile("(?m)^type\\s+(\\w+)\\s+(?:struct|interface)\\b");
    private static final Pattern GO_FUNC = Pattern.compile("(?m)^func\\s+(?:\\([^)]+\\)\\s+)?(\\w+)\\s*\\(");
    private static final Pattern RUST_TYPE = Pattern.compile("(?m)^\\s*(?:pub\\s+)?(?:struct|enum|trait|type)\\s+(\\w+)");
    private static final Pattern RUST_FN = Pattern.compile("(?m)^\\s*(?:pub\\s+)?(?:async\\s+)?fn\\s+(\\w+)\\s*(?:<|\\()");
    private static final Pattern DOC_LINE = Pattern.compile("(?m)^\\s*(?:#\\s+|///\\s+|//!\\s+|\\*\\s+)(.+)");

    private final LanguageFamily family;

    public LexicalUnderstander(LanguageFamily family) {
        this.family = family;
    }

    @Override
    public FileOutline outline(FileInfo file, DocLevel level, String source) {
        SourceScanner.Syntax syntax = family == null ? SourceScanner.Syntax.C_LIKE : family.syntax();
        String raw = source == null ? "" : source;
        String code = SourceScanner.blankCommentsAndLiterals(raw, syntax);
        List<DeclaredSymbol> symbols = new ArrayList<>();
        if (family == LanguageFamily.JAVASCRIPT) {
            addMatches(symbols, JS_CLASS, code, SymbolKind.CLASS);
            addMatches(symbols, JS_FUNCTION, code, SymbolKind.FUNCTION);
            addMatches(symbols, JS_CONST_FN, code, SymbolKind.FUNCTION);
        } else if (family == LanguageFamily.PYTHON) {
            addMatches(symbols, PY_CLASS, code, SymbolKind.CLASS);
            addMatches(symbols, PY_DEF, code, SymbolKind.FUNCTION);
        } else if (family == LanguageFamily.GO) {
            addMatches(symbols, GO_TYPE, code, SymbolKind.TYPE);
            addMatches(symbols, GO_FUNC, code, SymbolKind.FUNCTION);
        } else if (family == LanguageFamily.RUST) {
            addMatches(symbols, RUST_TYPE, code, SymbolKind.TYPE);
            addMatches(symbols, RUST_FN, code, SymbolKind.FUNCTION);
        } else {
            addTypeMatches(symbols, JAVA_LIKE_TYPE, code);
            addTypeMatches(symbols, JAVA_LIKE_TYPE_SHORT, code);
            addMatches(symbols, JAVA_LIKE_METHOD, code, SymbolKind.METHOD);
        }
        String doc = firstDoc(raw);
        if (!doc.isEmpty() && !symbols.isEmpty()) {
            DeclaredSymbol first = symbols.get(0);
            symbols.set(0, new DeclaredSymbol(first.getName(), first.getKind(), first.getLine(),
                    first.getVisibility(), doc, first.getAnnotations(), first.getParents(),
                    first.getMethods(), first.getReferences()));
        }
        return new FileOutline(file, level, FileOutline.ENGINE_LEXICAL, true, "", new ArrayList<>(), unique(symbols));
    }

    private static void addMatches(List<DeclaredSymbol> symbols, Pattern pattern, String code, SymbolKind kind) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            String name = m.group(m.groupCount());
            if (isKeyword(name)) continue;
            symbols.add(new DeclaredSymbol(name, kind, lineOf(code, m.start()), "", "",
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        }
    }

    private static void addTypeMatches(List<DeclaredSymbol> symbols, Pattern pattern, String code) {
        Matcher m = pattern.matcher(code);
        while (m.find()) {
            String kindWord = m.group(1);
            String name = m.group(2);
            if (isKeyword(name)) continue;
            symbols.add(new DeclaredSymbol(name, typeKind(kindWord), lineOf(code, m.start()), "", "",
                    new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        }
    }

    private static SymbolKind typeKind(String word) {
        switch (word) {
            case "interface":
            case "trait":
                return SymbolKind.INTERFACE;
            case "enum":
                return SymbolKind.ENUM;
            case "record":
                return SymbolKind.RECORD;
            case "struct":
                return SymbolKind.STRUCT;
            case "annotation":
                return SymbolKind.ANNOTATION;
            default:
                return SymbolKind.CLASS;
        }
    }

    private static boolean isKeyword(String name) {
        return name == null || name.isEmpty()
                || name.equals("if") || name.equals("for") || name.equals("while") || name.equals("switch")
                || name.equals("catch") || name.equals("return") || name.equals("new") || name.equals("this")
                || name.equals("super") || name.equals("import") || name.equals("from") || name.equals("func")
                || name.equals("def") || name.equals("class") || name.equals("function");
    }

    private static List<DeclaredSymbol> unique(List<DeclaredSymbol> symbols) {
        Set<String> seen = new LinkedHashSet<>();
        List<DeclaredSymbol> out = new ArrayList<>();
        for (DeclaredSymbol symbol : symbols) {
            String key = symbol.getKind() + ":" + symbol.getName();
            if (seen.add(key)) out.add(symbol);
        }
        return out;
    }

    private static String firstDoc(String raw) {
        Matcher m = DOC_LINE.matcher(raw);
        if (m.find()) {
            return JavaAstUnderstander.firstSentence(m.group(1));
        }
        return "";
    }

    static int lineOf(String text, int offset) {
        int line = 1;
        int last = Math.min(offset, text.length());
        for (int i = 0; i < last; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }
}
