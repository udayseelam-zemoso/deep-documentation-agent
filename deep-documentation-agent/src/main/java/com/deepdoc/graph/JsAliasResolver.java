package com.deepdoc.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a non-relative JS/TS import specifier into candidate paths, the way TypeScript,
 * bundlers and package managers would. Candidates are returned in priority order; the
 * extractor takes the first one that is an actual file.
 *
 * <ol>
 *   <li><b>tsconfig/jsconfig {@code paths}</b> of the nearest {@code tsconfig.json} or
 *       {@code jsconfig.json} above the importing file, following {@code extends}.
 *       {@code "@/*": ["src/*"]} maps {@code @/api/client} to {@code src/api/client}.</li>
 *   <li><b>Workspace packages</b>: a {@code package.json} inside the repository whose
 *       {@code name} is the specifier, e.g. {@code @acme/ui} in a monorepo. The package entry
 *       is taken from {@code source}, {@code module} or {@code main}, else {@code src/index}
 *       or {@code index}.</li>
 *   <li><b>{@code baseUrl}</b>: {@code components/Button} relative to the configured base.</li>
 *   <li><b>Configured aliases</b> from {@code graph.jsAliases} in {@code application.yml}, for
 *       aliases that only exist in vite/webpack config.</li>
 *   <li><b>Conventions</b>: {@code @/x} and {@code ~/x} mean {@code <project>/src/x}, where the
 *       project is the nearest directory with a {@code package.json}.</li>
 * </ol>
 * Bare npm packages ({@code react}) produce candidates that do not exist, so they stay unresolved.
 */
final class JsAliasResolver {
    private static final Logger logger = LoggerFactory.getLogger(JsAliasResolver.class);
    private static final String[] CONFIG_FILES = {"tsconfig.json", "jsconfig.json"};
    private static final int MAX_EXTENDS_DEPTH = 8;

    /** The parts of compilerOptions that affect module resolution. */
    static final class CompilerPaths {
        static final CompilerPaths NONE = new CompilerPaths(null, null, Collections.emptyMap());

        /** Absolute baseUrl, or null when not set. */
        final Path baseUrl;
        /** Directory that {@code paths} targets are relative to: baseUrl if set, else the declaring config's directory. */
        final Path pathsBase;
        /** Pattern -> target patterns, in declaration order. */
        final Map<String, List<String>> paths;

        CompilerPaths(Path baseUrl, Path pathsBase, Map<String, List<String>> paths) {
            this.baseUrl = baseUrl;
            this.pathsBase = pathsBase;
            this.paths = paths;
        }
    }

    private final Path root;
    private final Map<String, String> configuredAliases;
    /** Workspace package name -> package directory. */
    private final Map<String, Path> workspacePackages = new HashMap<>();
    private final Map<Path, Optional<Path>> packageJsonDirOf = new HashMap<>();
    private final Map<Path, CompilerPaths> pathsOfDirectory = new HashMap<>();
    private final Map<Path, CompilerPaths> parsedConfigs = new HashMap<>();

    JsAliasResolver(Path root, Collection<Path> sourceFiles, Map<String, String> configuredAliases) {
        this.root = SourceSet.normalize(root);
        this.configuredAliases = configuredAliases;
        for (Path file : sourceFiles) {
            Path dir = SourceSet.normalize(file).getParent();
            while (dir != null && dir.startsWith(this.root)) {
                nearestPackageDir(dir);
                dir = dir.getParent();
            }
        }
        for (Optional<Path> packageDir : new ArrayList<>(packageJsonDirOf.values())) {
            packageDir.ifPresent(this::registerWorkspacePackage);
        }
    }

    /** Candidate paths (without extension guessing) for {@code spec} imported from {@code fromFile}. */
    List<Path> candidates(Path fromFile, String spec) {
        Path fromDir = SourceSet.normalize(fromFile).getParent();
        List<Path> result = new ArrayList<>();
        CompilerPaths compiler = compilerPathsFor(fromDir);

        String bestPattern = null;
        String bestCapture = null;
        int bestPrefixLength = -1;
        for (String pattern : compiler.paths.keySet()) {
            int star = pattern.indexOf('*');
            if (star < 0) {
                if (pattern.equals(spec) && pattern.length() > bestPrefixLength) {
                    bestPattern = pattern;
                    bestCapture = "";
                    bestPrefixLength = pattern.length();
                }
                continue;
            }
            String prefix = pattern.substring(0, star);
            String suffix = pattern.substring(star + 1);
            if (spec.length() >= prefix.length() + suffix.length() && spec.startsWith(prefix)
                    && spec.endsWith(suffix) && prefix.length() > bestPrefixLength) {
                bestPattern = pattern;
                bestCapture = spec.substring(prefix.length(), spec.length() - suffix.length());
                bestPrefixLength = prefix.length();
            }
        }
        if (bestPattern != null) {
            for (String target : compiler.paths.get(bestPattern)) {
                result.add(compiler.pathsBase.resolve(target.replace("*", bestCapture)).normalize());
            }
        }

        addWorkspacePackageCandidates(spec, result);

        if (compiler.baseUrl != null) {
            result.add(compiler.baseUrl.resolve(spec).normalize());
        }

        String aliasKey = null;
        for (String key : configuredAliases.keySet()) {
            if ((spec.equals(key) || spec.startsWith(key + "/")) && (aliasKey == null || key.length() > aliasKey.length())) {
                aliasKey = key;
            }
        }
        if (aliasKey != null) {
            String rest = spec.substring(aliasKey.length()).replaceFirst("^/", "");
            result.add(root.resolve(configuredAliases.get(aliasKey)).resolve(rest).normalize());
        }

        if (spec.startsWith("@/") || spec.startsWith("~/")) {
            Path project = nearestPackageDir(fromDir).orElse(root);
            result.add(project.resolve("src").resolve(spec.substring(2)).normalize());
            result.add(project.resolve(spec.substring(2)).normalize());
        }
        return result;
    }

    /** {@code @acme/ui} -> package entry; {@code @acme/ui/button} -> {@code <package>/button} and {@code <package>/src/button}. */
    private void addWorkspacePackageCandidates(String spec, List<Path> result) {
        String name = packageName(spec);
        Path dir = name == null ? null : workspacePackages.get(name);
        if (dir == null) {
            return;
        }
        String rest = spec.substring(name.length()).replaceFirst("^/", "");
        if (!rest.isEmpty()) {
            result.add(dir.resolve(rest).normalize());
            result.add(dir.resolve("src").resolve(rest).normalize());
            return;
        }
        JsonObject manifest = readJson(dir.resolve("package.json"));
        for (String field : new String[]{"source", "module", "main"}) {
            if (manifest != null && manifest.has(field) && manifest.get(field).isJsonPrimitive()) {
                result.add(dir.resolve(manifest.get(field).getAsString()).normalize());
            }
        }
        result.add(dir.resolve("src/index"));
        result.add(dir.resolve("index"));
    }

    /** {@code @scope/name/sub} -> {@code @scope/name}; {@code name/sub} -> {@code name}. */
    private static String packageName(String spec) {
        String[] parts = spec.split("/");
        if (spec.startsWith("@")) {
            return parts.length >= 2 ? parts[0] + "/" + parts[1] : null;
        }
        return parts[0];
    }

    private void registerWorkspacePackage(Path packageDir) {
        JsonObject manifest = readJson(packageDir.resolve("package.json"));
        if (manifest != null && manifest.has("name") && manifest.get("name").isJsonPrimitive()) {
            workspacePackages.putIfAbsent(manifest.get("name").getAsString(), packageDir);
        }
    }

    private Optional<Path> nearestPackageDir(Path dir) {
        Optional<Path> cached = packageJsonDirOf.get(dir);
        if (cached != null) {
            return cached;
        }
        Optional<Path> result;
        if (Files.isRegularFile(dir.resolve("package.json"))) {
            result = Optional.of(dir);
        } else if (dir.equals(root) || dir.getParent() == null || !dir.startsWith(root)) {
            result = Optional.empty();
        } else {
            result = nearestPackageDir(dir.getParent());
        }
        packageJsonDirOf.put(dir, result);
        return result;
    }

    /** compilerOptions of the nearest tsconfig.json/jsconfig.json at or above {@code dir} (within the repo). */
    private CompilerPaths compilerPathsFor(Path dir) {
        CompilerPaths cached = pathsOfDirectory.get(dir);
        if (cached != null) {
            return cached;
        }
        CompilerPaths result = null;
        for (String name : CONFIG_FILES) {
            Path config = dir.resolve(name);
            if (Files.isRegularFile(config)) {
                result = loadConfig(config, 0);
                break;
            }
        }
        if (result == null) {
            result = dir.equals(root) || dir.getParent() == null || !dir.startsWith(root)
                    ? CompilerPaths.NONE
                    : compilerPathsFor(dir.getParent());
        }
        pathsOfDirectory.put(dir, result);
        return result;
    }

    /**
     * Reads one config and the configs it {@code extends}. A child's {@code baseUrl} and
     * {@code paths} replace the inherited ones (TypeScript does not merge {@code paths}).
     * With several {@code extends} entries, later ones win.
     */
    private CompilerPaths loadConfig(Path config, int depth) {
        Path normalized = SourceSet.normalize(config);
        CompilerPaths cached = parsedConfigs.get(normalized);
        if (cached != null) {
            return cached;
        }
        // Guards against "extends" cycles while this config is being loaded.
        parsedConfigs.put(normalized, CompilerPaths.NONE);

        JsonObject json = readJson(normalized);
        CompilerPaths inherited = CompilerPaths.NONE;
        if (json != null && depth < MAX_EXTENDS_DEPTH && json.has("extends")) {
            JsonElement ext = json.get("extends");
            List<String> parents = new ArrayList<>();
            if (ext.isJsonArray()) {
                for (JsonElement e : ext.getAsJsonArray()) parents.add(e.getAsString());
            } else if (ext.isJsonPrimitive()) {
                parents.add(ext.getAsString());
            }
            for (String parent : parents) {
                Path parentPath = resolveExtends(normalized.getParent(), parent);
                if (parentPath != null) {
                    CompilerPaths p = loadConfig(parentPath, depth + 1);
                    inherited = merge(inherited, p);
                }
            }
        }

        CompilerPaths own = CompilerPaths.NONE;
        JsonObject options = json != null && json.has("compilerOptions") && json.get("compilerOptions").isJsonObject()
                ? json.getAsJsonObject("compilerOptions") : null;
        if (options != null) {
            Path baseUrl = options.has("baseUrl")
                    ? normalized.getParent().resolve(options.get("baseUrl").getAsString()).normalize() : null;
            Map<String, List<String>> paths = new LinkedHashMap<>();
            if (options.has("paths") && options.get("paths").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : options.getAsJsonObject("paths").entrySet()) {
                    List<String> targets = new ArrayList<>();
                    if (e.getValue().isJsonArray()) {
                        JsonArray array = e.getValue().getAsJsonArray();
                        for (JsonElement t : array) targets.add(t.getAsString());
                    }
                    paths.put(e.getKey(), targets);
                }
            }
            Path effectiveBase = baseUrl != null ? baseUrl : inherited.baseUrl;
            if (!paths.isEmpty()) {
                own = new CompilerPaths(effectiveBase, effectiveBase != null ? effectiveBase : normalized.getParent(), paths);
            } else {
                own = new CompilerPaths(effectiveBase, inherited.pathsBase, inherited.paths);
            }
        }
        CompilerPaths result = merge(inherited, own);
        parsedConfigs.put(normalized, result);
        return result;
    }

    private static CompilerPaths merge(CompilerPaths base, CompilerPaths override) {
        Path baseUrl = override.baseUrl != null ? override.baseUrl : base.baseUrl;
        if (!override.paths.isEmpty()) {
            return new CompilerPaths(baseUrl, override.pathsBase, override.paths);
        }
        return new CompilerPaths(baseUrl, base.pathsBase, base.paths);
    }

    /** Relative extends point at a file (".json" optional); package extends are looked up in node_modules. */
    private Path resolveExtends(Path configDir, String parent) {
        List<Path> candidates = new ArrayList<>();
        if (parent.startsWith(".") || parent.startsWith("/")) {
            Path p = configDir.resolve(parent).normalize();
            candidates.add(p);
            candidates.add(p.resolveSibling(p.getFileName() + ".json"));
        } else {
            Path p = root.resolve("node_modules").resolve(parent);
            candidates.add(p);
            candidates.add(p.resolveSibling(p.getFileName() + ".json"));
            candidates.add(p.resolve("tsconfig.json"));
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Parses tsconfig-style JSON, which allows comments and trailing commas.
     * Returns null for unreadable or invalid files.
     */
    static JsonObject readJson(Path file) {
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            text = SourceScanner.blankComments(text, SourceScanner.Syntax.JS);
            text = text.replaceAll(",(\\s*[}\\]])", "$1");
            JsonReader reader = new JsonReader(new StringReader(text));
            reader.setLenient(true);
            JsonElement element = JsonParser.parseReader(reader);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (IOException | RuntimeException e) {
            logger.debug("Ignoring unreadable JSON {}: {}", file, e.getMessage());
            return null;
        }
    }
}
