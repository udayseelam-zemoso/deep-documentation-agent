package com.deepdoc.analyzer;

import com.deepdoc.config.AnalysisSection;
import com.deepdoc.model.ArchitectureStyle;
import com.deepdoc.model.DetectedModule;
import com.deepdoc.model.DetectedPattern;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.RepositoryContext;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.model.SourceReference;
import com.deepdoc.utils.SourceFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills {@link RepositoryContext} with frameworks, build tools, datastores, build modules and
 * an architecture label plus confidence.
 *
 * <p>Frameworks come from {@code architectures.yml}: each pattern is a weighted set of signals
 * (a file exists, a file contains a marker, a file name matches). Confidence is the fraction of
 * that weight that actually hit, so a repo with only {@code @SpringBootApplication} scores lower
 * than one that also has {@code spring-boot} in the POM and an {@code application.yml}.
 *
 * <p>The <em>style</em> (monolith / modular monolith / microservices / library) is not a YAML
 * pattern. It is counted from modules: how many can be deployed on their own, whether they sit
 * behind one parent build, and whether Docker Compose or several Dockerfiles say they run
 * separately. That keeps "Spring Boot" and "this is three services" as two independent facts.
 *
 * <p>The architecture string the CLI prints is then composed: a backend plus a frontend becomes
 * {@code Full-stack (Spring Boot + React)}; several deployable units become
 * {@code Spring Boot microservices}.
 */
public class ArchitectureDetector {
    private static final Logger logger = LoggerFactory.getLogger(ArchitectureDetector.class);
    private static final int MAX_EVIDENCE = 3;

    private final ArchitectureCatalog catalog;
    private final double minConfidence;

    public ArchitectureDetector(AnalysisSection config) {
        this(config, ArchitectureCatalog.getDefault());
    }

    ArchitectureDetector(AnalysisSection config, ArchitectureCatalog catalog) {
        this.catalog = catalog;
        this.minConfidence = config.getArchitectureMinConfidence();
    }

    public void detect(RepositoryManifest manifest) {
        Path root = manifest.getRepositoryPath();
        List<FileInfo> files = manifest.getFiles();
        Map<Path, String> contents = new HashMap<>();

        List<DetectedModule> modules = ModuleDetector.detect(root, files);
        List<DetectedPattern> patterns = new ArrayList<>();
        for (ArchitecturePattern pattern : catalog.getPatterns()) {
            DetectedPattern match = evaluate(pattern, root, files, contents);
            if (match != null && match.getConfidence() + 1e-9 >= minConfidence) {
                patterns.add(match);
            }
        }
        dropSuppressed(patterns, catalog.getPatterns());
        patterns.sort(Comparator.comparingDouble(DetectedPattern::getConfidence).reversed()
                .thenComparing(DetectedPattern::getName));

        ArchitectureStyle style = classifyStyle(root, files, modules);
        ChosenArchitecture chosen = chooseLabel(patterns, style);

        RepositoryContext context = manifest.getContext();
        context.setDetectedArchitecture(chosen.label);
        context.setArchitectureConfidence(chosen.confidence);
        context.setArchitectureStyle(style);
        context.setPatterns(patterns);
        context.setModules(modules);
        for (DetectedPattern pattern : patterns) {
            if (pattern.isFramework()) {
                context.addFramework(pattern.getName());
            } else if (DetectedPattern.KIND_BUILD.equals(pattern.getKind())) {
                context.addTechnology("build", pattern.getName());
            } else if (DetectedPattern.KIND_DATASTORE.equals(pattern.getKind())) {
                context.addTechnology("datastore", pattern.getName());
            }
        }

        logger.info("Detected architecture: {} ({}, {})", chosen.label,
                percent(chosen.confidence), style.displayName());
        logger.info("Detected frameworks: {}", context.getFrameworks());
        if (!modules.isEmpty()) {
            logger.info("Detected modules: {}", modules.size());
        }
    }

    /**
     * When two patterns match the same stack, keep the more specific one. Spring Boot always
     * also looks like Spring; Next.js always also looks like React.
     */
    static void dropSuppressed(List<DetectedPattern> patterns, List<ArchitecturePattern> catalog) {
        HashSet<String> present = new HashSet<>();
        for (DetectedPattern pattern : patterns) present.add(pattern.getName());
        HashSet<String> hide = new HashSet<>();
        for (ArchitecturePattern definition : catalog) {
            if (present.contains(definition.getName()) && definition.getSuppresses() != null) {
                hide.addAll(definition.getSuppresses());
            }
        }
        Iterator<DetectedPattern> it = patterns.iterator();
        while (it.hasNext()) {
            if (hide.contains(it.next().getName())) it.remove();
        }
    }

    /**
     * Confidence is matched-weight / total-weight. A miss is a zero, not a skip, so a pattern
     * with three equal signals and one hit is 0.33, not 1.0.
     */
    private DetectedPattern evaluate(ArchitecturePattern pattern, Path root, List<FileInfo> files,
                                     Map<Path, String> contents) {
        double total = 0;
        double matched = 0;
        List<SourceReference> evidence = new ArrayList<>();
        for (ArchitectureSignal signal : pattern.getSignals()) {
            total += Math.max(0, signal.getWeight());
            SourceReference hit = match(signal, root, files, contents);
            if (hit != null) {
                matched += Math.max(0, signal.getWeight());
                if (evidence.size() < MAX_EVIDENCE) evidence.add(hit);
            }
        }
        if (total <= 0 || matched <= 0) return null;
        return new DetectedPattern(pattern.getName(), pattern.getKind(), pattern.getLayer(),
                pattern.getArchitecture(), matched / total, evidence);
    }

    private SourceReference match(ArchitectureSignal signal, Path root, List<FileInfo> files,
                                  Map<Path, String> contents) {
        String type = signal.getType() == null ? "" : signal.getType().toLowerCase(Locale.ROOT);
        switch (type) {
            case "file-exists":
                return firstExisting(root, signal.getPaths());
            case "directory":
                return firstDirectory(root, signal.getPaths());
            case "filename":
                return firstNamed(root, files, signal.getFiles());
            case "file-count":
                return countNamed(root, files, signal.getFiles(), signal.getMin());
            case "file-contains":
                return firstContaining(root, files, contents, signal);
            default:
                logger.debug("Unknown architecture signal type: {}", signal.getType());
                return null;
        }
    }

    private static SourceReference firstExisting(Path root, List<String> paths) {
        for (String path : paths) {
            Path resolved = root.resolve(path);
            if (Files.isRegularFile(resolved)) {
                return new SourceReference(resolved, 1, 1, path);
            }
        }
        return null;
    }

    private static SourceReference firstDirectory(Path root, List<String> paths) {
        for (String path : paths) {
            Path resolved = root.resolve(path);
            if (Files.isDirectory(resolved)) {
                return new SourceReference(resolved, 1, 1, path + "/");
            }
        }
        return null;
    }

    private static SourceReference firstNamed(Path root, List<FileInfo> files, List<String> names) {
        for (FileInfo file : files) {
            if (nameMatches(file, names)) {
                return new SourceReference(file.getFilePath(), 1, 1,
                        ModuleDetector.relative(root, file.getFilePath()));
            }
        }
        return null;
    }

    private static SourceReference countNamed(Path root, List<FileInfo> files, List<String> names, int min) {
        int count = 0;
        FileInfo first = null;
        for (FileInfo file : files) {
            if (nameMatches(file, names)) {
                count++;
                if (first == null) first = file;
            }
        }
        if (count < min || first == null) return null;
        return new SourceReference(first.getFilePath(), 1, 1, count + " files");
    }

    private static SourceReference firstContaining(Path root, List<FileInfo> files,
                                                   Map<Path, String> contents,
                                                   ArchitectureSignal signal) {
        Pattern pattern = signal.isRegex() ? Pattern.compile(signal.getText()) : null;
        for (FileInfo file : files) {
            if (!nameMatches(file, signal.getFiles())) continue;
            String text = contents.computeIfAbsent(file.getFilePath(), SourceFiles::read);
            if (text == null) continue;
            int offset = indexOf(text, signal.getText(), pattern);
            if (offset >= 0) {
                int line = lineOf(text, offset);
                return new SourceReference(file.getFilePath(), line, line, lineText(text, offset));
            }
        }
        return null;
    }

    /**
     * A {@code files} entry is an extension when it starts with a dot ({@code .java}), a
     * prefix when it ends with {@code .*} ({@code Dockerfile.*}), otherwise an exact file name.
     */
    static boolean nameMatches(FileInfo file, List<String> names) {
        if (names == null || names.isEmpty()) return false;
        String fileName = file.getFilePath().getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String raw : names) {
            if (raw == null || raw.isEmpty()) continue;
            if (raw.startsWith(".") && !raw.endsWith(".*")) {
                if (lower.endsWith(raw.toLowerCase(Locale.ROOT))) return true;
            } else if (raw.endsWith(".*")) {
                if (fileName.startsWith(raw.substring(0, raw.length() - 1))) return true;
            } else if (fileName.equals(raw)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOf(String text, String needle, Pattern regex) {
        if (regex != null) {
            Matcher m = regex.matcher(text);
            return m.find() ? m.start() : -1;
        }
        return needle == null || needle.isEmpty() ? -1 : text.indexOf(needle);
    }

    static int lineOf(String text, int offset) {
        int line = 1;
        int last = Math.min(offset, text.length());
        for (int i = 0; i < last; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static String lineText(String text, int offset) {
        int start = offset;
        while (start > 0 && text.charAt(start - 1) != '\n') start--;
        int end = offset;
        while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') end++;
        String line = text.substring(start, end).trim();
        return line.length() > 80 ? line.substring(0, 77) + "..." : line;
    }

    /**
     * Microservices when two or more units look independently shippable <em>and</em> operations
     * files agree (Compose application services, several Dockerfiles, or sibling service
     * directories). Several Maven modules with a single {@code @SpringBootApplication} stay a
     * modular monolith: that is one product split for the compiler, not several products.
     */
    static ArchitectureStyle classifyStyle(Path root, List<FileInfo> files, List<DetectedModule> modules) {
        int deployable = 0;
        for (DetectedModule module : modules) {
            if (module.isDeployable()) deployable++;
        }
        int composeApps = ModuleDetector.applicationServiceCount(root);
        int dockerfiles = ModuleDetector.dockerfileCount(files);
        boolean serviceDirs = hasServiceLayout(modules);

        if (deployable >= 2 && (composeApps >= 2 || dockerfiles >= 2 || serviceDirs)) {
            return ArchitectureStyle.MICROSERVICES;
        }
        if (deployable >= 2) {
            // Two mains under one parent build, no ops evidence: one product with two binaries.
            return ArchitectureStyle.MODULAR_MONOLITH;
        }
        if (deployable == 1 && modules.size() >= 2) {
            return ArchitectureStyle.MODULAR_MONOLITH;
        }
        if (deployable == 1) {
            return ArchitectureStyle.MONOLITH;
        }
        if (!modules.isEmpty() && !files.isEmpty()) {
            return ArchitectureStyle.LIBRARY;
        }
        return ArchitectureStyle.UNKNOWN;
    }

    /** Conventional layouts: {@code services/orders} and {@code services/billing}, or {@code apps/*}. */
    private static boolean hasServiceLayout(List<DetectedModule> modules) {
        int underServices = 0;
        int underApps = 0;
        for (DetectedModule module : modules) {
            String path = module.getRelativePath();
            if (path.startsWith("services/") && !"services".equals(path)) underServices++;
            if (path.startsWith("apps/") && !"apps".equals(path)) underApps++;
        }
        return underServices >= 2 || underApps >= 2;
    }

    /**
     * Picks the sentence the CLI prints. Spring Boot plus React is a full-stack app, not two
     * competing answers; microservices keep the framework name and change only the ending.
     */
    static ChosenArchitecture chooseLabel(List<DetectedPattern> patterns, ArchitectureStyle style) {
        DetectedPattern backend = bestLayer(patterns, "backend");
        DetectedPattern frontend = bestLayer(patterns, "frontend");
        DetectedPattern mobile = bestLayer(patterns, "mobile");
        DetectedPattern serverLess = named(patterns, "Serverless");

        if (serverLess != null && serverLess.getConfidence() >= 0.5) {
            String label = backend == null ? "Serverless Application"
                    : "Serverless (" + backend.getName() + ")";
            return new ChosenArchitecture(label, serverLess.getConfidence());
        }
        if (mobile != null && (backend == null || mobile.getConfidence() >= backend.getConfidence())) {
            return new ChosenArchitecture(labelOf(mobile), mobile.getConfidence());
        }
        if (backend != null && frontend != null) {
            String label = "Full-stack (" + backend.getName() + " + " + frontend.getName() + ")";
            if (style == ArchitectureStyle.MICROSERVICES) {
                label = backend.getName() + " + " + frontend.getName() + " microservices";
            }
            return new ChosenArchitecture(label, (backend.getConfidence() + frontend.getConfidence()) / 2);
        }
        DetectedPattern primary = backend != null ? backend : frontend;
        if (primary == null) {
            if (style == ArchitectureStyle.MICROSERVICES) {
                return new ChosenArchitecture("Microservices", 0.6);
            }
            if (style == ArchitectureStyle.LIBRARY) {
                return new ChosenArchitecture("Library", 0.4);
            }
            return new ChosenArchitecture("Unknown", 0);
        }
        String label = labelOf(primary);
        if (style == ArchitectureStyle.MICROSERVICES) {
            label = primary.getName() + " microservices";
        }
        return new ChosenArchitecture(label, primary.getConfidence());
    }

    private static DetectedPattern bestLayer(List<DetectedPattern> patterns, String layer) {
        DetectedPattern best = null;
        for (DetectedPattern pattern : patterns) {
            if (!pattern.isFramework()) continue;
            if (!layer.equals(pattern.getLayer())) continue;
            if (best == null || pattern.getConfidence() > best.getConfidence()) best = pattern;
        }
        return best;
    }

    private static DetectedPattern named(List<DetectedPattern> patterns, String name) {
        for (DetectedPattern pattern : patterns) {
            if (name.equals(pattern.getName())) return pattern;
        }
        return null;
    }

    private static String labelOf(DetectedPattern pattern) {
        return pattern.getArchitectureLabel() == null || pattern.getArchitectureLabel().isEmpty()
                ? pattern.getName() : pattern.getArchitectureLabel();
    }

    public static String percent(double confidence) {
        return Math.round(confidence * 100) + "%";
    }

    static final class ChosenArchitecture {
        final String label;
        final double confidence;

        ChosenArchitecture(String label, double confidence) {
            this.label = label;
            this.confidence = confidence;
        }
    }
}
