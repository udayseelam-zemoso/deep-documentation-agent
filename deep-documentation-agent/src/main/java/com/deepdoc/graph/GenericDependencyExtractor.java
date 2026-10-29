package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;
import com.deepdoc.utils.LanguageCatalog;
import com.deepdoc.utils.LanguageDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback for programming languages without a dedicated extractor (Clojure, Elixir, Dart, Lua,
 * Haskell, Perl, Erlang, ...). It knows nothing about any one language's import syntax; instead it
 * relies on three conventions that almost every language follows. One instance handles the files
 * of a single language, and only links files of that language to each other.
 *
 * <ol>
 *   <li><b>Quoted paths.</b> A string literal naming a file, e.g. Lua {@code require("app.util")},
 *       Dart {@code import 'src/cart.dart'}, Julia {@code include("solver.jl")}. Relative paths
 *       resolve against the file's directory; others are matched as a path suffix.</li>
 *   <li><b>Module names.</b> Most languages map module names onto directories: Haskell
 *       {@code Data.Cart} lives in {@code Data/Cart.hs}, Perl {@code Shop::Cart} in
 *       {@code Shop/Cart.pm}, Clojure {@code shop.cart-utils} in {@code shop/cart_utils.clj},
 *       Elixir {@code Shop.CartItem} in {@code shop/cart_item.ex}. Every dotted, {@code ::} or
 *       {@code /} separated name in the code is turned into those path forms and looked up,
 *       as is the single name after an import-like keyword ({@code require cart}).</li>
 *   <li><b>Unique declarations.</b> A capitalised type or module name ({@code class Cart},
 *       {@code defmodule Cart}, {@code data Cart}) declared in exactly one file links every
 *       other file that mentions it to that file. Names declared in several files are skipped
 *       because the reference would be ambiguous.</li>
 * </ol>
 *
 * <p>Comments are blanked first when the language's comment syntax is known (see
 * {@link #syntaxOf}); for the rest, commented-out code may add a few spurious edges, which the
 * ranking tolerates far better than a file having no edges at all.
 */
final class GenericDependencyExtractor implements DependencyExtractor {
    private static final Pattern STRING = Pattern.compile("\"([^\"\\s]{1,200})\"|'([^'\\s]{1,200})'");
    private static final Pattern MODULE_NAME =
            Pattern.compile("(?<![\\w.:/-])[A-Za-z_][\\w-]*(?:(?:\\.|::|/)[A-Za-z_][\\w-]*)+");
    private static final Pattern IMPORT_KEYWORD = Pattern.compile(
            "\\b(?:import|require|use|include|load|open|using|alias|source)\\b[\\s(]*([A-Za-z_][\\w-]*)\\b");
    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(?:class|struct|interface|trait|protocol|enum|record|type|typealias|object|module|defmodule"
                    + "|defprotocol|defrecord|deftype|data|newtype|contract|library|mixin|extension|package)"
                    + "\\s+([A-Z][\\w.]*)"
                    + "|^-module\\(\\s*(\\w+)",       // Erlang: module names are lower-case atoms
            Pattern.MULTILINE);
    private static final int MIN_SYMBOL_LENGTH = 3;

    private final SourceSet sources;
    private final List<String> extensions;
    private final SourceScanner.Syntax syntax;
    /** Declared symbol (simple name) -> the one file declaring it; ambiguous names are removed. */
    private final Map<String, FileInfo> declarer = new HashMap<>();
    private final Map<FileInfo, Set<String>> declaredBy = new HashMap<>();

    GenericDependencyExtractor(SourceSet sources, String language, Collection<FileInfo> files) {
        this.sources = sources;
        LanguageDefinition definition = LanguageCatalog.getDefault().byName(language);
        this.extensions = definition == null ? Collections.emptyList() : definition.getExtensions();
        this.syntax = syntaxOf(language);
        indexDeclarations(files);
    }

    /**
     * Comment syntax for languages whose comments look like ones {@link SourceScanner} knows.
     * Null means "do not blank": Lisp ({@code ;}), Haskell/Lua ({@code --}) and Erlang
     * ({@code %}) comments are not understood, and guessing wrong would hide real code.
     */
    static SourceScanner.Syntax syntaxOf(String language) {
        switch (language) {
            case "Dart":
            case "Zig":
            case "Solidity":
            case "F#":
                return SourceScanner.Syntax.C_LIKE;
            case "Perl":
            case "R":
            case "Julia":
            case "Elixir":
            case "Crystal":
            case "Nim":
            case "PowerShell":
                return SourceScanner.Syntax.RUBY;
            default:
                return null;
        }
    }

    private void indexDeclarations(Collection<FileInfo> files) {
        Set<String> ambiguous = new HashSet<>();
        for (FileInfo file : files) {
            Set<String> names = new LinkedHashSet<>();
            Matcher m = DECLARATION.matcher(code(file));
            while (m.find()) {
                String name = m.group(1) != null ? m.group(1) : m.group(2);
                // Dotted module names (Elixir "Shop.Cart") are referenced by their last segment.
                name = name.substring(name.lastIndexOf('.') + 1);
                if (name.length() >= MIN_SYMBOL_LENGTH) names.add(name);
            }
            declaredBy.put(file, names);
            for (String name : names) {
                FileInfo previous = declarer.putIfAbsent(name, file);
                if (previous != null && !previous.equals(file)) ambiguous.add(name);
            }
        }
        ambiguous.forEach(declarer::remove);
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String code = code(file);
        LineIndex lines = new LineIndex(code);
        DependencyCollector deps = new DependencyCollector(file);

        Matcher strings = STRING.matcher(code);
        while (strings.find()) {
            String literal = strings.group(1) != null ? strings.group(1) : strings.group(2);
            deps.add(resolveQuoted(file, literal), 1, lines.lineOf(strings.start()));
        }

        Matcher imports = IMPORT_KEYWORD.matcher(code);
        while (imports.find()) {
            deps.addOnce(resolveModule(file, imports.group(1)), 1, lines.lineOf(imports.start()));
        }

        Matcher modules = MODULE_NAME.matcher(code);
        while (modules.find()) {
            deps.addOnce(resolveModule(file, modules.group()), 1, lines.lineOf(modules.start()));
        }

        Map<String, int[]> tokens = SourceScanner.countIdentifiers(code);
        Set<String> own = declaredBy.getOrDefault(file, Collections.emptySet());
        for (Map.Entry<String, FileInfo> symbol : declarer.entrySet()) {
            int uses = SourceScanner.count(tokens, symbol.getKey());
            if (uses > 0 && !own.contains(symbol.getKey())) {
                deps.addOnce(symbol.getValue(), uses, SourceScanner.firstLine(tokens, symbol.getKey()));
            }
        }
        return deps.toList();
    }

    private String code(FileInfo file) {
        String source = sources.source(file);
        return syntax == null ? source : SourceScanner.blankComments(source, syntax);
    }

    /** A quoted string: an explicit (relative) file path, or a module name written as a string. */
    private FileInfo resolveQuoted(FileInfo from, String literal) {
        String spec = literal.replaceFirst("^package:[^/]+/", "");   // Dart package URIs
        if (spec.startsWith("./") || spec.startsWith("../")) {
            Path base = SourceSet.normalize(from.getFilePath()).getParent().resolve(spec).normalize();
            FileInfo exact = sources.at(base);
            if (exact != null) return exact;
            for (String ext : extensions) {
                FileInfo withExt = sources.at(base.resolveSibling(base.getFileName() + ext));
                if (withExt != null) return withExt;
            }
            return null;
        }
        if (hasOwnExtension(spec)) {
            return sources.findBySuffix(spec, from, this::sameLanguage);
        }
        return resolveModule(from, spec);
    }

    /** Tries each path form of a module name, with each of the language's file extensions. */
    private FileInfo resolveModule(FileInfo from, String name) {
        for (String path : pathForms(name)) {
            for (String ext : extensions) {
                FileInfo target = sources.findBySuffix(path + ext, from, this::sameLanguage);
                if (target != null) return target;
            }
        }
        return null;
    }

    /**
     * Path spellings of a module name: separators become {@code /}, then the Clojure form
     * ({@code -} to {@code _}) and the Elixir/Crystal form (CamelCase to snake_case).
     */
    static Set<String> pathForms(String name) {
        String path = name.replace("::", "/").replace('.', '/');
        Set<String> forms = new LinkedHashSet<>();
        forms.add(path);
        forms.add(path.replace('-', '_'));
        forms.add(snakeCase(path));
        return forms;
    }

    private static String snakeCase(String path) {
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            segments.add(segment.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT));
        }
        return String.join("/", segments);
    }

    private boolean hasOwnExtension(String spec) {
        for (String ext : extensions) {
            if (spec.endsWith(ext)) return true;
        }
        return false;
    }

    private boolean sameLanguage(FileInfo candidate) {
        String name = candidate.getFilePath().getFileName().toString();
        return hasOwnExtension(name);
    }
}
