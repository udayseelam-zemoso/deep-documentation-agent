package com.deepdoc.analyzer;

import com.deepdoc.model.DetectedModule;
import com.deepdoc.model.FileInfo;
import com.deepdoc.utils.SourceFiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Finds build modules (Maven, Gradle, npm workspaces, Go, Cargo, Python, .NET) so the
 * architecture detector can tell a single app from a modular monolith or a set of services.
 *
 * <p>A module is <em>deployable</em> when it looks independently runnable: a
 * {@code @SpringBootApplication} / {@code main}, a Dockerfile, or a start script. Shared
 * library modules stay non-deployable.
 */
final class ModuleDetector {
    private static final Pattern MAVEN_PARENT = Pattern.compile("(?s)<parent>.*?</parent>");
    private static final Pattern ARTIFACT_ID = Pattern.compile("<artifactId>\\s*([^<]+)\\s*</artifactId>");
    private static final Pattern MAVEN_MODULE = Pattern.compile("<module>\\s*([^<]+)\\s*</module>");
    private static final Pattern GRADLE_INCLUDE = Pattern.compile("include\\s*\\(?([^\\n)]+)");
    private static final Pattern QUOTED = Pattern.compile("[\"']:?([^\"']+)[\"']");
    private static final Pattern WORKSPACES_ARRAY = Pattern.compile("\"workspaces\"\\s*:\\s*\\[([^\\]]+)]");
    private static final Pattern WORKSPACES_PACKAGES = Pattern.compile("\"packages\"\\s*:\\s*\\[([^\\]]+)]");
    private static final Pattern GO_MODULE = Pattern.compile("(?m)^module\\s+(\\S+)");
    private static final Pattern CARGO_MEMBERS = Pattern.compile("(?s)\\[workspace].*?members\\s*=\\s*\\[([^]]+)]");
    private static final Pattern PACKAGE_JSON_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    private static final Pattern DEPLOYABLE_SOURCE = Pattern.compile(
            "@SpringBootApplication"
                    + "|public\\s+static\\s+void\\s+main\\s*\\("
                    + "|^fun\\s+main\\s*\\("
                    + "|\\bfn\\s+main\\s*\\("
                    + "|\\bfunc\\s+main\\s*\\("
                    + "|\\b__name__\\s*=="
                    + "|\\bWebApplication\\.CreateBuilder\\s*\\("
                    + "|@(?:main|UIApplicationMain)\\b",
            Pattern.MULTILINE);

    private ModuleDetector() {
    }

    static List<DetectedModule> detect(Path root, List<FileInfo> files) {
        Map<String, Proto> found = new LinkedHashMap<>();
        collectMaven(root, files, found);
        collectGradle(root, files, found);
        collectNpm(root, files, found);
        collectGo(root, files, found);
        collectCargo(root, files, found);
        collectPython(root, files, found);
        collectDotNet(root, files, found);

        if (found.isEmpty() && !files.isEmpty()) {
            found.put(".", new Proto("root", ".", "directory"));
        }

        List<String> modulePaths = new ArrayList<>(found.keySet());
        List<DetectedModule> modules = new ArrayList<>();
        for (Proto proto : found.values()) {
            modules.add(new DetectedModule(proto.name, proto.relativePath, proto.buildSystem,
                    isDeployable(root, proto.relativePath, files, modulePaths)));
        }
        modules.sort(Comparator.comparing(DetectedModule::getRelativePath));
        return modules;
    }

    private static void collectMaven(Path root, List<FileInfo> files, Map<String, Proto> found) {
        for (FileInfo file : named(files, "pom.xml")) {
            String text = SourceFiles.read(file.getFilePath());
            if (text == null) continue;
            String dir = directoryOf(root, file);
            String withoutParent = MAVEN_PARENT.matcher(text).replaceAll("");
            Matcher artifact = ARTIFACT_ID.matcher(withoutParent);
            String name = artifact.find() ? artifact.group(1).trim() : nameFrom(dir);
            found.putIfAbsent(dir, new Proto(name, dir, "Maven"));
            Matcher modules = MAVEN_MODULE.matcher(text);
            while (modules.find()) {
                String child = join(dir, modules.group(1).trim());
                found.putIfAbsent(child, new Proto(nameFrom(child), child, "Maven"));
            }
        }
    }

    private static void collectGradle(Path root, List<FileInfo> files, Map<String, Proto> found) {
        for (FileInfo file : files) {
            String name = file.getFilePath().getFileName().toString();
            if (!name.equals("settings.gradle") && !name.equals("settings.gradle.kts")) continue;
            String text = SourceFiles.read(file.getFilePath());
            if (text == null) continue;
            String dir = directoryOf(root, file);
            found.putIfAbsent(dir, new Proto(nameFrom(dir), dir, "Gradle"));
            Matcher include = GRADLE_INCLUDE.matcher(text);
            while (include.find()) {
                Matcher quoted = QUOTED.matcher(include.group(1));
                while (quoted.find()) {
                    String child = join(dir, quoted.group(1).trim().replace(':', '/'));
                    found.putIfAbsent(child, new Proto(nameFrom(child), child, "Gradle"));
                }
            }
        }
        for (FileInfo file : files) {
            String name = file.getFilePath().getFileName().toString();
            if (!name.equals("build.gradle") && !name.equals("build.gradle.kts")) continue;
            String dir = directoryOf(root, file);
            found.putIfAbsent(dir, new Proto(nameFrom(dir), dir, "Gradle"));
        }
    }

    private static void collectNpm(Path root, List<FileInfo> files, Map<String, Proto> found) {
        for (FileInfo file : named(files, "package.json")) {
            String text = SourceFiles.read(file.getFilePath());
            if (text == null) continue;
            String dir = directoryOf(root, file);
            Matcher name = PACKAGE_JSON_NAME.matcher(text);
            String packageName = name.find() ? name.group(1) : nameFrom(dir);
            found.putIfAbsent(dir, new Proto(packageName, dir, "npm"));
            List<String> globs = new ArrayList<>();
            collectQuoted(WORKSPACES_ARRAY.matcher(text), globs);
            collectQuoted(WORKSPACES_PACKAGES.matcher(text), globs);
            for (String glob : globs) {
                expandWorkspaceGlob(root, dir, glob, found);
            }
        }
    }

    private static void collectGo(Path root, List<FileInfo> files, Map<String, Proto> found) {
        for (FileInfo file : named(files, "go.mod")) {
            String text = SourceFiles.read(file.getFilePath());
            String dir = directoryOf(root, file);
            Matcher m = text == null ? null : GO_MODULE.matcher(text);
            String name = m != null && m.find() ? m.group(1) : nameFrom(dir);
            found.putIfAbsent(dir, new Proto(name, dir, "Go"));
        }
    }

    private static void collectCargo(Path root, List<FileInfo> files, Map<String, Proto> found) {
        for (FileInfo file : named(files, "Cargo.toml")) {
            String text = SourceFiles.read(file.getFilePath());
            String dir = directoryOf(root, file);
            found.putIfAbsent(dir, new Proto(nameFrom(dir), dir, "Cargo"));
            if (text == null) continue;
            Matcher members = CARGO_MEMBERS.matcher(text);
            if (members.find()) {
                Matcher quoted = QUOTED.matcher(members.group(1));
                while (quoted.find()) {
                    String child = join(dir, quoted.group(1).trim());
                    found.putIfAbsent(child, new Proto(nameFrom(child), child, "Cargo"));
                }
            }
        }
    }

    private static void collectPython(Path root, List<FileInfo> files, Map<String, Proto> found) {
        for (FileInfo file : files) {
            String name = file.getFilePath().getFileName().toString();
            if (!name.equals("pyproject.toml") && !name.equals("setup.py")) continue;
            String dir = directoryOf(root, file);
            found.putIfAbsent(dir, new Proto(nameFrom(dir), dir, "Python"));
        }
    }

    private static void collectDotNet(Path root, List<FileInfo> files, Map<String, Proto> found) {
        for (FileInfo file : files) {
            if (!file.getFilePath().getFileName().toString().endsWith(".csproj")) continue;
            String dir = directoryOf(root, file);
            String name = file.getFilePath().getFileName().toString().replace(".csproj", "");
            found.putIfAbsent(dir, new Proto(name, dir, ".NET"));
        }
    }

    /**
     * A module is deployable if it has a Dockerfile, a start script, or a source-level entry
     * point. Files in a nested child module are not counted for the parent, so a root aggregator
     * pom does not become deployable just because a child is.
     */
    static boolean isDeployable(Path root, String modulePath, List<FileInfo> files,
                                List<String> allModulePaths) {
        Path moduleDir = ".".equals(modulePath) ? root : root.resolve(modulePath);
        if (Files.isRegularFile(moduleDir.resolve("Dockerfile"))
                || Files.isRegularFile(moduleDir.resolve("manage.py"))
                || Files.isRegularFile(moduleDir.resolve("artisan"))) {
            return true;
        }
        for (FileInfo file : files) {
            if (!belongsToModule(root, modulePath, file, allModulePaths)) continue;
            String name = file.getFilePath().getFileName().toString();
            if (name.equals("Dockerfile") || name.startsWith("Dockerfile.")) return true;
            String text = SourceFiles.read(file.getFilePath());
            if (text == null) continue;
            if (name.equals("package.json") && (text.contains("\"start\"") || text.contains("\"next\""))) {
                return true;
            }
            if (DEPLOYABLE_SOURCE.matcher(text).find()) return true;
        }
        return false;
    }

    /**
     * A file belongs to {@code modulePath} when it sits in that directory, and to no more
     * specific detected module. The parent of {@code api/src/...} is {@code api}, not {@code .}.
     */
    static boolean belongsToModule(Path root, String modulePath, FileInfo file,
                                   List<String> allModulePaths) {
        String rel = relative(root, file.getFilePath());
        if (!under(modulePath, rel)) return false;
        for (String other : allModulePaths) {
            if (!other.equals(modulePath) && moreSpecific(other, modulePath) && under(other, rel)) {
                return false;
            }
        }
        return true;
    }

    private static boolean under(String modulePath, String fileRel) {
        if (".".equals(modulePath)) return true;
        return fileRel.equals(modulePath) || fileRel.startsWith(modulePath + "/");
    }

    /** {@code child} is nested inside {@code parent} (root {@code "."} is a parent of everything). */
    private static boolean moreSpecific(String child, String parent) {
        if (child.equals(parent)) return false;
        if (".".equals(parent)) return !".".equals(child);
        return child.startsWith(parent + "/");
    }

    private static void expandWorkspaceGlob(Path root, String from, String glob, Map<String, Proto> found) {
        if (!glob.endsWith("/*")) {
            String child = join(from, glob);
            found.putIfAbsent(child, new Proto(nameFrom(child), child, "npm"));
            return;
        }
        Path dir = ".".equals(from) ? root.resolve(glob.substring(0, glob.length() - 2))
                : root.resolve(from).resolve(glob.substring(0, glob.length() - 2));
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.filter(Files::isDirectory).collect(Collectors.toList())) {
                String rel = relative(root, child);
                found.putIfAbsent(rel, new Proto(child.getFileName().toString(), rel, "npm"));
            }
        } catch (Exception ignored) {
            // A listing failure just means this workspace glob contributes no extra modules.
        }
    }

    private static void collectQuoted(Matcher matcher, List<String> into) {
        if (matcher.find()) {
            Matcher quoted = QUOTED.matcher(matcher.group(1));
            while (quoted.find()) into.add(quoted.group(1).trim());
        }
    }

    private static List<FileInfo> named(List<FileInfo> files, String fileName) {
        List<FileInfo> match = new ArrayList<>();
        for (FileInfo file : files) {
            if (file.getFilePath().getFileName().toString().equals(fileName)) match.add(file);
        }
        return match;
    }

    private static String directoryOf(Path root, FileInfo file) {
        Path parent = file.getFilePath().getParent();
        if (parent == null) return ".";
        return relative(root, parent);
    }

    static String relative(Path root, Path path) {
        Path rel = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        String text = rel.toString().replace('\\', '/');
        return text.isEmpty() ? "." : text;
    }

    private static String join(String dir, String child) {
        String cleaned = child.replace('\\', '/').replaceAll("^\\./", "");
        if (".".equals(dir) || dir.isEmpty()) return cleaned;
        return dir + "/" + cleaned;
    }

    private static String nameFrom(String relativePath) {
        if (".".equals(relativePath) || relativePath.isEmpty()) return "root";
        int slash = relativePath.lastIndexOf('/');
        return slash < 0 ? relativePath : relativePath.substring(slash + 1);
    }

    /** How many docker-compose services look like applications, not databases. */
    static int applicationServiceCount(Path root) {
        for (String name : new String[] {"docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml"}) {
            Path file = root.resolve(name);
            if (!Files.isRegularFile(file)) continue;
            String text = SourceFiles.read(file);
            if (text != null) return countComposeServices(text);
        }
        return 0;
    }

    static int countComposeServices(String text) {
        int servicesAt = indexOfServices(text);
        if (servicesAt < 0) return 0;
        int count = 0;
        String[] lines = text.substring(servicesAt).split("\n", -1);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;
            // A less-indented key ends the services block (compose top-level keys sit at column 0).
            if (line.matches("^[A-Za-z].*")) break;
            Matcher m = Pattern.compile("^  ([A-Za-z0-9_-]+):\\s*(#.*)?$").matcher(line);
            if (m.matches() && !isInfrastructureService(m.group(1))) count++;
        }
        return count;
    }

    static int dockerfileCount(List<FileInfo> files) {
        int count = 0;
        for (FileInfo file : files) {
            String name = file.getFilePath().getFileName().toString();
            if (name.equals("Dockerfile") || name.startsWith("Dockerfile.")) count++;
        }
        return count;
    }

    private static int indexOfServices(String text) {
        Matcher m = Pattern.compile("(?m)^services:\\s*$").matcher(text);
        return m.find() ? m.start() : -1;
    }

    private static boolean isInfrastructureService(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("postgres") || n.contains("mysql") || n.contains("mongo")
                || n.contains("redis") || n.contains("rabbit") || n.contains("kafka")
                || n.contains("zookeeper") || n.contains("elastic") || n.contains("kibana")
                || n.contains("nginx") || n.contains("traefik") || n.contains("prometheus")
                || n.contains("grafana") || n.contains("minio") || n.equals("db")
                || n.equals("cache");
    }

    private static final class Proto {
        final String name;
        final String relativePath;
        final String buildSystem;

        Proto(String name, String relativePath, String buildSystem) {
            this.name = name;
            this.relativePath = relativePath;
            this.buildSystem = buildSystem;
        }
    }
}
