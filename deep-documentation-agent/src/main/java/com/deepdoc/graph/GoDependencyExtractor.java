package com.deepdoc.graph;

import com.deepdoc.model.FileInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dependencies between Go files.
 *
 * <p>Go imports whole packages (directories), not files. To still get file-level edges, each
 * file's package-level declarations ({@code func}, {@code type}, {@code var}, {@code const}) are
 * indexed, and a use of {@code alias.Name} becomes an edge to the file that declares
 * {@code Name}. If an import is used but no declaring file can be identified, the edge goes to
 * the package's "main" file: {@code <dir>.go}, {@code doc.go}, or the first file.
 *
 * <p><b>Import paths</b> map to directories through the {@code module} line of the nearest
 * {@code go.mod}: with {@code module github.com/acme/shop}, the import
 * {@code github.com/acme/shop/internal/order} is {@code <module dir>/internal/order}.
 * Imports from other modules (the standard library, dependencies) do not resolve.
 *
 * <p><b>Same-package</b> files use each other's declarations without imports, so identifiers
 * declared in a sibling file of the same package also create edges.
 */
final class GoDependencyExtractor implements DependencyExtractor {
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+(\\w+)");
    private static final Pattern IMPORT_SINGLE = Pattern.compile("(?m)^import\\s+(?:(\\w+|\\.|_)\\s+)?\"([^\"]+)\"");
    private static final Pattern IMPORT_BLOCK = Pattern.compile("(?ms)^import\\s*\\((.*?)^\\)");
    private static final Pattern IMPORT_SPEC = Pattern.compile("(?m)^\\s*(?:(\\w+|\\.|_)\\s+)?\"([^\"]+)\"");
    private static final Pattern MODULE = Pattern.compile("(?m)^module\\s+(\\S+)");
    /** func Name( - functions without a receiver; methods are reached through their type. */
    private static final Pattern FUNC = Pattern.compile("(?m)^func\\s+(\\w+)");
    private static final Pattern SINGLE_DECL = Pattern.compile("(?m)^(?:type|var|const)\\s+(\\w+)");
    private static final Pattern GROUP_DECL = Pattern.compile("(?ms)^(?:type|var|const)\\s*\\((.*?)^\\)");
    private static final Pattern GROUP_ITEM = Pattern.compile("(?m)^\\s+(\\w+)");
    private static final Pattern SELECTOR = Pattern.compile("\\b(\\w+)\\.(\\w+)");
    private static final Set<String> IGNORED_DECLARATIONS = new HashSet<>(Arrays.asList("_", "init", "main"));

    private final SourceSet sources;
    private final Map<FileInfo, String> code = new HashMap<>();
    private final Map<FileInfo, String> packageName = new HashMap<>();
    /** directory -> declared identifier -> declaring file */
    private final Map<Path, Map<String, FileInfo>> declarations = new HashMap<>();
    /** module directory -> module path, from go.mod */
    private final Map<Path, String> modules = new LinkedHashMap<>();
    private final Set<Path> visitedDirectories = new HashSet<>();

    GoDependencyExtractor(SourceSet sources, Collection<FileInfo> files) {
        this.sources = sources;
        for (FileInfo file : files) {
            String cleaned = SourceScanner.blankComments(sources.source(file), SourceScanner.Syntax.GO);
            code.put(file, cleaned);
            Matcher pkg = PACKAGE.matcher(cleaned);
            packageName.put(file, pkg.find() ? pkg.group(1) : "");
            Map<String, FileInfo> declared = declarations.computeIfAbsent(directory(file), k -> new HashMap<>());
            for (String name : declaredNames(SourceScanner.blankCommentsAndLiterals(cleaned, SourceScanner.Syntax.GO))) {
                declared.putIfAbsent(name, file);
            }
            findModule(directory(file));
        }
    }

    private static List<String> declaredNames(String code) {
        List<String> names = new ArrayList<>();
        for (Pattern p : new Pattern[]{FUNC, SINGLE_DECL}) {
            Matcher m = p.matcher(code);
            while (m.find()) names.add(m.group(1));
        }
        Matcher group = GROUP_DECL.matcher(code);
        while (group.find()) {
            Matcher item = GROUP_ITEM.matcher(group.group(1));
            while (item.find()) names.add(item.group(1));
        }
        names.removeAll(IGNORED_DECLARATIONS);
        return names;
    }

    @Override
    public List<RawDependency> extract(FileInfo file) {
        String withStrings = code.get(file);
        LineIndex lines = new LineIndex(withStrings);
        String body = SourceScanner.blankLinesStartingWith(
                SourceScanner.blankCommentsAndLiterals(withStrings, SourceScanner.Syntax.GO), "package", "import");
        Map<String, int[]> tokens = SourceScanner.countIdentifiers(body);
        Map<String, Map<String, int[]>> selectors = selectorUses(body, lines);
        DependencyCollector deps = new DependencyCollector(file);

        for (String[] imp : imports(withStrings, lines)) {
            Path dir = resolveImport(imp[1]);
            if (dir == null) continue;
            int line = Integer.parseInt(imp[2]);
            List<FileInfo> pkgFiles = sources.inDirectory(dir);
            if (pkgFiles.isEmpty()) continue;
            String alias = imp[0] != null ? imp[0] : packageName.getOrDefault(pkgFiles.get(0), lastSegment(imp[1]));
            Map<String, FileInfo> declared = declarations.getOrDefault(dir, new HashMap<>());
            boolean linked = false;
            if (".".equals(alias)) {
                for (Map.Entry<String, FileInfo> d : declared.entrySet()) {
                    int uses = SourceScanner.count(tokens, d.getKey());
                    if (uses > 0) {
                        deps.add(d.getValue(), uses, line);
                        linked = true;
                    }
                }
            } else {
                for (Map.Entry<String, int[]> use : selectors.getOrDefault(alias, new HashMap<>()).entrySet()) {
                    FileInfo target = declared.get(use.getKey());
                    if (target != null) {
                        deps.add(target, use.getValue()[0], line);
                        linked = true;
                    }
                }
            }
            if (!linked) {
                deps.add(mainFile(dir, pkgFiles), 1, line);
            }
        }

        Map<String, FileInfo> siblings = declarations.getOrDefault(directory(file), new HashMap<>());
        for (Map.Entry<String, FileInfo> d : siblings.entrySet()) {
            FileInfo sibling = d.getValue();
            if (sibling.equals(file) || !packageName.get(sibling).equals(packageName.get(file))) continue;
            int uses = SourceScanner.count(tokens, d.getKey());
            if (uses > 0) {
                deps.add(sibling, uses, SourceScanner.firstLine(tokens, d.getKey()));
            }
        }
        return deps.toList();
    }

    /** {alias or null, import path, line} for single-line and grouped imports. */
    private static List<String[]> imports(String code, LineIndex lines) {
        List<String[]> result = new ArrayList<>();
        Matcher single = IMPORT_SINGLE.matcher(code);
        while (single.find()) {
            result.add(new String[]{single.group(1), single.group(2), String.valueOf(lines.lineOf(single.start()))});
        }
        Matcher block = IMPORT_BLOCK.matcher(code);
        while (block.find()) {
            Matcher spec = IMPORT_SPEC.matcher(block.group(1));
            while (spec.find()) {
                int offset = block.start(1) + spec.start(2);
                result.add(new String[]{spec.group(1), spec.group(2), String.valueOf(lines.lineOf(offset))});
            }
        }
        return result;
    }

    /** alias -> member -> {uses, first line}, from every {@code x.Y} in the body. */
    private static Map<String, Map<String, int[]>> selectorUses(String body, LineIndex lines) {
        Map<String, Map<String, int[]>> uses = new HashMap<>();
        Matcher m = SELECTOR.matcher(body);
        while (m.find()) {
            int[] entry = uses.computeIfAbsent(m.group(1), k -> new HashMap<>())
                    .computeIfAbsent(m.group(2), k -> new int[]{0, lines.lineOf(m.start())});
            entry[0]++;
        }
        return uses;
    }

    /** Import path -> directory inside this repository, via the longest matching go.mod module path. */
    private Path resolveImport(String importPath) {
        Path best = null;
        int bestLength = -1;
        for (Map.Entry<Path, String> module : modules.entrySet()) {
            String modulePath = module.getValue();
            if ((importPath.equals(modulePath) || importPath.startsWith(modulePath + "/"))
                    && modulePath.length() > bestLength) {
                String rest = importPath.substring(modulePath.length()).replaceFirst("^/", "");
                best = rest.isEmpty() ? module.getKey() : module.getKey().resolve(rest).normalize();
                bestLength = modulePath.length();
            }
        }
        return best;
    }

    /** Walks up from {@code dir} to the repository root looking for go.mod; each directory is checked once. */
    private void findModule(Path dir) {
        if (!visitedDirectories.add(dir)) return;
        Path goMod = dir.resolve("go.mod");
        if (Files.isRegularFile(goMod)) {
            try {
                Matcher m = MODULE.matcher(new String(Files.readAllBytes(goMod), StandardCharsets.UTF_8));
                if (m.find()) {
                    modules.put(dir, m.group(1));
                }
            } catch (IOException ignored) {
                // An unreadable go.mod just means imports of that module stay unresolved.
            }
        } else if (dir.startsWith(sources.root()) && !dir.equals(sources.root()) && dir.getParent() != null) {
            findModule(dir.getParent());
        }
    }

    private static FileInfo mainFile(Path dir, List<FileInfo> files) {
        String dirName = dir.getFileName() == null ? "" : dir.getFileName().toString();
        for (String preferred : new String[]{dirName + ".go", "doc.go"}) {
            for (FileInfo f : files) {
                if (f.getFilePath().getFileName().toString().equals(preferred)) return f;
            }
        }
        return files.stream().min((a, b) -> a.getFilePath().compareTo(b.getFilePath())).orElse(null);
    }

    private static Path directory(FileInfo file) {
        return SourceSet.normalize(file.getFilePath()).getParent();
    }

    private static String lastSegment(String importPath) {
        String[] parts = importPath.split("/");
        String last = parts[parts.length - 1];
        // github.com/x/y/v2 is imported as "y"
        return last.matches("v\\d+") && parts.length > 1 ? parts[parts.length - 2] : last;
    }
}
