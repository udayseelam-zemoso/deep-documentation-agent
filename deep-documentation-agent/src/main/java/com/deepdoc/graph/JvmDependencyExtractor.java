package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between Java, Kotlin, Scala and Groovy files of the same repository.
 * All four share packages and imports, and can reference each other, so one index covers them.
 *
 * <p><b>Index.</b> Every file is registered under {@code package.TypeName} for each top-level type
 * it declares. For Java and Groovy that is the file name ({@code Order.java} declares
 * {@code Order}); Kotlin and Scala files may declare several types with any names, so their
 * declarations are read from the source. Kotlin top-level functions and properties are also
 * registered, so {@code import com.acme.util.formatMoney} resolves to the declaring file.
 *
 * <p><b>Edges</b> come from four places, in this order. Explicit imports of the same file add up;
 * the later steps skip targets already found, so the same usages are never counted twice:
 * <ol>
 *   <li>explicit imports - {@code import a.b.Foo}, {@code import a.b.Outer.Inner},
 *       {@code import static a.b.Util.max}, Kotlin {@code import a.b.Foo as Bar}</li>
 *   <li>wildcard imports - {@code a.b.*} and Scala {@code a.b._} / {@code a.b.{X, Y => Z}};
 *       only classes actually mentioned in the body become edges</li>
 *   <li>fully qualified names in code - {@code new com.acme.util.Money(5)} with no import</li>
 *   <li>same-package usage, which needs no import at all</li>
 * </ol>
 * The reference count of an edge is how often the imported name appears in the body, at least 1
 * for an explicit import.
 */
final class JvmDependencyExtractor implements DependencyExtractor {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)");
    /** import [static] a.b.C[.* | ._] [as Alias] [;]  (one per line) */
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^[ \\t]*import[ \\t]+(static[ \\t]+)?([\\w.]+?)(\\.\\*|\\._)?(?:[ \\t]+as[ \\t]+(\\w+))?[ \\t]*;?[ \\t]*$");
    /** Scala grouped import: import a.b.{C, D => E, _} */
    private static final Pattern SCALA_GROUP_IMPORT =
            Pattern.compile("(?m)^[ \\t]*import[ \\t]+([\\w.]+)\\.\\{([^}]*)\\}");
    /** Kotlin/Scala type declared at column 0, after any modifiers. */
    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^(?:(?:public|private|protected|internal|open|abstract|sealed|data|enum|annotation|inner"
                    + "|value|final|case|implicit|fun)\\s+)*(?:class|interface|object|trait)\\s+([A-Za-z_]\\w*)");
    /** Kotlin top-level function or property, including extension functions (fun String.slug()). */
    private static final Pattern TOP_LEVEL_MEMBER = Pattern.compile(
            "(?m)^(?:(?:public|private|internal|inline|suspend|operator|infix|tailrec|const)\\s+)*"
                    + "(?:fun|val|var)\\s+(?:<[^>]*>\\s*)?(?:[\\w.]+\\.)?(\\w+)");
    /** Dotted name starting with a lower-case segment, as packages do: com.acme.util.Money */
    private static final Pattern QUALIFIED_NAME = Pattern.compile("\\b[a-z_]\\w*(?:\\.[A-Za-z_]\\w*)+");

    private final Map<FileInfo, String> cleanedSources = new HashMap<>();
    private final Map<FileInfo, String> packageOf = new HashMap<>();
    /** package.Name -> declaring file (types and Kotlin top-level members) */
    private final Map<String, FileInfo> byQualifiedName = new HashMap<>();
    /** package -> type name -> declaring file; types only, used for wildcard and same-package lookups */
    private final Map<String, Map<String, FileInfo>> typesByPackage = new HashMap<>();

    JvmDependencyExtractor(SourceSet sources, Collection<FileInfo> files) {
        for (FileInfo file : files) {
            String cleaned = SourceScanner.blankCommentsAndLiterals(sources.source(file), SourceScanner.Syntax.C_LIKE);
            cleanedSources.put(file, cleaned);
            Matcher m = PACKAGE.matcher(cleaned);
            String pkg = m.find() ? m.group(1) : "";
            packageOf.put(file, pkg);

            for (String type : declaredTypes(file, cleaned)) {
                byQualifiedName.put(qualify(pkg, type), file);
                typesByPackage.computeIfAbsent(pkg, k -> new LinkedHashMap<>()).put(type, file);
            }
            if ("Kotlin".equals(file.getLanguage())) {
                Matcher member = TOP_LEVEL_MEMBER.matcher(cleaned);
                while (member.find()) {
                    byQualifiedName.putIfAbsent(qualify(pkg, member.group(1)), file);
                }
            }
        }
    }

    private static List<String> declaredTypes(FileInfo file, String cleaned) {
        List<String> types = new ArrayList<>();
        String language = file.getLanguage();
        if ("Java".equals(language) || "Groovy".equals(language)) {
            types.add(simpleFileName(file));
            return types;
        }
        Matcher m = TOP_LEVEL_TYPE.matcher(cleaned);
        while (m.find()) {
            types.add(m.group(1));
        }
        return types;
    }

    @Override
    public String namespaceOf(FileInfo file) {
        return packageOf.getOrDefault(file, "");
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String cleaned = cleanedSources.get(file);
        if (cleaned == null) {
            return new ArrayList<>();
        }
        LineIndex lines = new LineIndex(cleaned);
        String body = SourceScanner.blankLinesStartingWith(cleaned, "package", "import");
        Map<String, int[]> tokens = SourceScanner.countIdentifiers(body);
        DependencyCollector deps = new DependencyCollector(file);

        Matcher m = IMPORT.matcher(cleaned);
        while (m.find()) {
            String name = m.group(2);
            int line = lines.lineOf(m.start(2));
            if (m.group(3) != null) {
                addWildcard(deps, name, tokens, line);
            } else {
                String usedAs = m.group(4) != null ? m.group(4) : lastSegment(name);
                addExplicit(deps, name, usedAs, tokens, line);
            }
        }

        Matcher group = SCALA_GROUP_IMPORT.matcher(cleaned);
        while (group.find()) {
            String prefix = group.group(1);
            int line = lines.lineOf(group.start(1));
            for (String item : group.group(2).split(",")) {
                String[] parts = item.trim().split("\\s*=>\\s*");
                if (parts[0].isEmpty()) continue;
                if (parts[0].equals("_")) {
                    addWildcard(deps, prefix, tokens, line);
                } else {
                    String usedAs = parts.length > 1 && !parts[1].equals("_") ? parts[1] : parts[0];
                    addExplicit(deps, prefix + "." + parts[0], usedAs, tokens, line);
                }
            }
        }

        addQualifiedNames(deps, body, lines);

        Map<String, FileInfo> samePackage = typesByPackage.getOrDefault(namespaceOf(file), new HashMap<>());
        Map<FileInfo, int[]> siblings = new LinkedHashMap<>();
        samePackage.forEach((type, sibling) -> {
            int uses = SourceScanner.count(tokens, type);
            if (uses > 0 && !sibling.equals(file)) {
                int[] entry = siblings.computeIfAbsent(sibling, k -> new int[]{0, SourceScanner.firstLine(tokens, type)});
                entry[0] += uses;
            }
        });
        siblings.forEach((sibling, v) -> deps.addOnce(sibling, v[0], v[1]));

        return deps.toList();
    }

    /** Each explicit import names something different, so imports of one file add up. */
    private void addExplicit(DependencyCollector deps, String name, String usedAs, Map<String, int[]> tokens, int line) {
        FileInfo target = resolve(name);
        deps.add(target, Math.max(1, SourceScanner.count(tokens, usedAs)), line);
    }

    /** {@code a.b.*}: a package (types mentioned in the body) or a class (static members, count 1). */
    private void addWildcard(DependencyCollector deps, String name, Map<String, int[]> tokens, int line) {
        Map<String, FileInfo> types = typesByPackage.get(name);
        if (types == null) {
            deps.addOnce(resolve(name), 1, line);
            return;
        }
        types.forEach((type, target) -> {
            int uses = SourceScanner.count(tokens, type);
            if (uses > 0) deps.addOnce(target, uses, SourceScanner.firstLine(tokens, type));
        });
    }

    /**
     * Fully qualified references in code. Each dotted chain is resolved like an import; chains that
     * do not start with a known package ({@code this.order.total}, {@code System.out}) resolve to nothing.
     */
    private void addQualifiedNames(DependencyCollector deps, String body, LineIndex lines) {
        Map<FileInfo, int[]> found = new LinkedHashMap<>();
        Matcher m = QUALIFIED_NAME.matcher(body);
        while (m.find()) {
            FileInfo target = resolve(m.group());
            if (target == null) continue;
            int[] entry = found.computeIfAbsent(target, k -> new int[]{0, lines.lineOf(m.start())});
            entry[0]++;
        }
        found.forEach((target, v) -> deps.addOnce(target, v[0], v[1]));
    }

    /**
     * Resolves a dotted name to the file declaring it, dropping trailing segments until a known
     * name is reached: {@code a.b.Outer.Inner} and {@code a.b.Util.max} both resolve to the file
     * of {@code a.b.Outer} / {@code a.b.Util}. Stops before a single segment, so a bare word never
     * matches a default-package class by accident.
     */
    private FileInfo resolve(String name) {
        String candidate = name;
        while (candidate.indexOf('.') > 0) {
            FileInfo file = byQualifiedName.get(candidate);
            if (file != null) {
                return file;
            }
            candidate = candidate.substring(0, candidate.lastIndexOf('.'));
        }
        return name.indexOf('.') < 0 ? byQualifiedName.get(name) : null;
    }

    private static String qualify(String pkg, String name) {
        return pkg.isEmpty() ? name : pkg + "." + name;
    }

    private static String lastSegment(String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    static String simpleFileName(FileInfo file) {
        String fileName = file.getFilePath().getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }
}
