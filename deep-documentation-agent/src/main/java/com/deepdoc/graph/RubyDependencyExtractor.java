package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between Ruby files.
 *
 * <ul>
 *   <li>{@code require_relative 'x'} - relative to the requiring file.</li>
 *   <li>{@code require 'acme/x'} / {@code load} - matched as a path suffix, so files under
 *       {@code lib/} (the usual load path) are found; gems have no file in the repository.</li>
 *   <li>Constant references, for Rails and other Zeitwerk-style autoloading where files are
 *       never required: {@code Billing::InvoiceMailer} is looked up as
 *       {@code billing/invoice_mailer.rb}, then as {@code invoice_mailer.rb} (a constant used
 *       inside its own namespace), anywhere in the repository, closest file first.</li>
 * </ul>
 * Constants the file defines itself ({@code class Order}) do not count.
 */
final class RubyDependencyExtractor implements DependencyExtractor {
    private static final Pattern REQUIRE_RELATIVE = Pattern.compile("\\brequire_relative\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern REQUIRE = Pattern.compile("\\b(?:require|load)\\s*\\(?\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern CONSTANT = Pattern.compile("\\b[A-Z]\\w*(?:::[A-Z]\\w*)*");
    private static final Pattern DEFINITION = Pattern.compile("\\b(?:class|module)\\s+([A-Z][\\w:]*)");

    private final SourceSet sources;
    /** Memoized constant -> file lookups; the same constants appear in many files. */
    private final Map<String, FileInfo> constantCache = new HashMap<>();
    private final Set<String> unresolvedConstants = new HashSet<>();

    RubyDependencyExtractor(SourceSet sources) {
        this.sources = sources;
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String withStrings = SourceScanner.blankComments(sources.source(file), SourceScanner.Syntax.RUBY);
        String code = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.RUBY);
        LineIndex lines = new LineIndex(withStrings);
        DependencyCollector deps = new DependencyCollector(file);
        Path dir = SourceSet.normalize(file.getFilePath()).getParent();

        Matcher rel = REQUIRE_RELATIVE.matcher(withStrings);
        while (rel.find()) {
            Path target = dir.resolve(withRb(rel.group(1))).normalize();
            deps.add(sources.at(target), 1, lines.lineOf(rel.start()));
        }
        Matcher req = REQUIRE.matcher(withStrings);
        while (req.find()) {
            deps.add(sources.findBySuffix(withRb(req.group(1)), file, RubyDependencyExtractor::isRuby), 1,
                    lines.lineOf(req.start()));
        }

        Set<String> defined = new HashSet<>();
        Matcher def = DEFINITION.matcher(code);
        while (def.find()) {
            String name = def.group(1);
            defined.add(name);
            defined.add(name.substring(name.lastIndexOf(':') + 1));
        }
        Matcher constant = CONSTANT.matcher(code);
        while (constant.find()) {
            String name = constant.group();
            if (defined.contains(name)) continue;
            deps.add(resolveConstant(name, file), 1, lines.lineOf(constant.start()));
        }
        return deps.toList();
    }

    private FileInfo resolveConstant(String constant, FileInfo from) {
        if (unresolvedConstants.contains(constant)) return null;
        FileInfo cached = constantCache.get(constant);
        if (cached != null) return cached;
        String path = underscore(constant) + ".rb";
        FileInfo target = sources.findBySuffix(path, from, RubyDependencyExtractor::isRuby);
        if (target == null && constant.contains("::")) {
            target = sources.findBySuffix(path.substring(path.lastIndexOf('/') + 1), from, RubyDependencyExtractor::isRuby);
        }
        if (target == null) {
            unresolvedConstants.add(constant);
        } else {
            constantCache.put(constant, target);
        }
        return target;
    }

    /** {@code Billing::InvoiceMailer} -> {@code billing/invoice_mailer}; {@code HTTPClient} -> {@code http_client}. */
    static String underscore(String constant) {
        return constant.replace("::", "/")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z\\d])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    private static String withRb(String path) {
        return path.endsWith(".rb") ? path : path + ".rb";
    }

    private static boolean isRuby(FileInfo file) {
        return "Ruby".equals(file.getLanguage());
    }
}
