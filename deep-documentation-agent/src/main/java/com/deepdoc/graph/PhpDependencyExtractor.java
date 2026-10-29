package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between PHP files.
 *
 * <p>Classes are indexed by fully qualified name, {@code namespace\ClassName}, from each file's
 * {@code namespace} declaration and its {@code class}/{@code interface}/{@code trait}/{@code enum}
 * declarations. PHP class names are case-insensitive, so lookups are too. Edges come from:
 * <ul>
 *   <li>{@code use A\B\C;}, {@code use A\B\C as D;} and grouped {@code use A\B\{C, D};}</li>
 *   <li>fully qualified names in code: {@code new \A\B\C()}</li>
 *   <li>classes of the same namespace used by short name (no {@code use} needed)</li>
 *   <li>{@code require}/{@code include} of a file path, relative to the file
 *       ({@code __DIR__ . '/x.php'}) or the repository root</li>
 * </ul>
 */
final class PhpDependencyExtractor implements DependencyExtractor {
    private static final Pattern NAMESPACE = Pattern.compile("(?m)^\\s*namespace\\s+([\\w\\\\]+)\\s*[;{]");
    private static final Pattern TYPE = Pattern.compile("\\b(?:class|interface|trait|enum)\\s+([A-Za-z_]\\w*)");
    private static final Pattern USE = Pattern.compile(
            "(?m)^\\s*use\\s+(?:function\\s+|const\\s+)?\\\\?([\\w\\\\]+?)(?:\\\\\\{([^}]*)\\})?(?:\\s+as\\s+(\\w+))?\\s*;");
    private static final Pattern QUALIFIED_NAME = Pattern.compile("\\\\?[A-Za-z_]\\w*(?:\\\\[A-Za-z_]\\w*)+");
    private static final Pattern INCLUDE = Pattern.compile(
            "\\b(?:require|include)(?:_once)?\\s*\\(?\\s*(__DIR__\\s*\\.\\s*)?['\"]([^'\"]+\\.php)['\"]");

    private final SourceSet sources;
    private final Map<FileInfo, String> namespaceOf = new HashMap<>();
    /** lower-cased namespace\Class -> file */
    private final Map<String, FileInfo> byQualifiedName = new HashMap<>();
    /** namespace -> class name -> file */
    private final Map<String, Map<String, FileInfo>> classesByNamespace = new HashMap<>();

    PhpDependencyExtractor(SourceSet sources, Collection<FileInfo> files) {
        this.sources = sources;
        for (FileInfo file : files) {
            String code = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.PHP);
            Matcher ns = NAMESPACE.matcher(code);
            String namespace = ns.find() ? ns.group(1) : "";
            namespaceOf.put(file, namespace);
            Matcher type = TYPE.matcher(code);
            while (type.find()) {
                String name = type.group(1);
                byQualifiedName.put(qualify(namespace, name).toLowerCase(Locale.ROOT), file);
                classesByNamespace.computeIfAbsent(namespace, k -> new LinkedHashMap<>()).put(name, file);
            }
        }
    }

    @Override
    public String namespaceOf(FileInfo file) {
        return namespaceOf.get(file).replace('\\', '.');
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String withStrings = SourceScanner.blankComments(sources.source(file), SourceScanner.Syntax.PHP);
        String code = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.PHP);
        LineIndex lines = new LineIndex(code);
        String body = SourceScanner.blankLinesStartingWith(code, "use", "namespace");
        Map<String, int[]> tokens = SourceScanner.countIdentifiers(body);
        DependencyCollector deps = new DependencyCollector(file);

        Matcher use = USE.matcher(code);
        while (use.find()) {
            int line = lines.lineOf(use.start());
            if (use.group(2) != null) {
                for (String item : use.group(2).split(",")) {
                    String[] parts = item.trim().split("\\s+as\\s+");
                    if (parts[0].isEmpty()) continue;
                    String usedAs = parts.length > 1 ? parts[1] : lastSegment(parts[0]);
                    addUse(deps, use.group(1) + "\\" + parts[0], usedAs, tokens, line);
                }
            } else {
                String usedAs = use.group(3) != null ? use.group(3) : lastSegment(use.group(1));
                addUse(deps, use.group(1), usedAs, tokens, line);
            }
        }

        Matcher qualified = QUALIFIED_NAME.matcher(body);
        while (qualified.find()) {
            String name = qualified.group().replaceFirst("^\\\\", "");
            deps.add(byQualifiedName.get(name.toLowerCase(Locale.ROOT)), 1, lines.lineOf(qualified.start()));
        }

        for (Map.Entry<String, FileInfo> sibling : classesByNamespace.getOrDefault(namespaceOf.get(file), new HashMap<>()).entrySet()) {
            int uses = SourceScanner.count(tokens, sibling.getKey());
            if (uses > 0) {
                deps.addOnce(sibling.getValue(), uses, SourceScanner.firstLine(tokens, sibling.getKey()));
            }
        }

        Matcher include = INCLUDE.matcher(withStrings);
        while (include.find()) {
            String path = include.group(2);
            Path dir = SourceSet.normalize(file.getFilePath()).getParent();
            FileInfo target = sources.at(dir.resolve(path.replaceFirst("^/", "")));
            if (target == null) target = sources.at(sources.root().resolve(path.replaceFirst("^/", "")));
            deps.add(target, 1, lines.lineOf(include.start()));
        }
        return deps.toList();
    }

    private void addUse(DependencyCollector deps, String name, String usedAs, Map<String, int[]> tokens, int line) {
        FileInfo target = byQualifiedName.get(name.toLowerCase(Locale.ROOT));
        deps.add(target, Math.max(1, SourceScanner.count(tokens, usedAs)), line);
    }

    private static String qualify(String namespace, String name) {
        return namespace.isEmpty() ? name : namespace + "\\" + name;
    }

    private static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('\\') + 1);
    }
}
