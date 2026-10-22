package com.deepdoc.cli;

import com.deepdoc.analyzer.RepositoryAnalyzer;
import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.graph.DependencyGraphBuilder;
import com.deepdoc.graph.PackageEdge;
import com.deepdoc.graph.PackageNode;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.RepositoryManifest;
import org.jgrapht.Graph;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
    name = "deep-doc-agent",
    mixinStandardHelpOptions = true,
    version = "Deep Documentation Agent 1.0.0",
    description = "Generates high-quality, explanatory documentation for software repositories"
)
public class DeepDocCLI implements Callable<Integer> {
    private static final Logger logger = LoggerFactory.getLogger(DeepDocCLI.class);

    @Command(name = "analyze", description = "Analyze a repository and generate documentation")
    public Integer analyze(
            @Parameters(index = "0", description = "Path to the repository to analyze")
            String repositoryPath,
            
            @Option(names = {"-o", "--output"}, description = "Output directory for documentation (default: <repo>/docs)")
            String outputPath,
            
            @Option(names = {"-v", "--verbose"}, description = "Enable verbose logging")
            boolean verbose,
            
            @Option(names = {"--no-llm"}, description = "Skip LLM inference (structure-only documentation)")
            boolean noLlm
    ) {
        try {
            AppConfig config = ConfigLoader.load();
            logger.info("{} v{}", config.getApp().getName(), config.getApp().getVersion());
            logger.info("=================================");
            logger.info("Loaded settings from src/main/resources/application.yml");
            logger.info("");

            Path repoPath = Paths.get(repositoryPath).toAbsolutePath().normalize();
            logger.info("Analyzing repository: {}", repoPath);
            logger.info("");
            
            RepositoryAnalyzer analyzer = new RepositoryAnalyzer(config);
            RepositoryManifest manifest = analyzer.analyze(repoPath);
            
            printManifestSummary(manifest);

            DependencyGraph graph = new DependencyGraphBuilder(config.getGraph()).build(manifest);
            printDependencyGraph(graph);
            
            logger.info("");
            logger.info("✓ Repository analysis completed successfully");
            logger.info("");
            logger.info("Next steps:");
            logger.info("  - Code understanding and LLM integration (Week 2)");
            logger.info("  - Documentation generation (Week 3)");
            
            return 0;
        } catch (IOException e) {
            logger.error("Failed to analyze repository: {}", e.getMessage());
            if (verbose) {
                logger.error("Stack trace:", e);
            }
            return 1;
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            if (verbose) {
                logger.error("Stack trace:", e);
            }
            return 1;
        }
    }

    private void printManifestSummary(RepositoryManifest manifest) {
        logger.info("Repository Analysis Summary");
        logger.info("---------------------------");
        logger.info("Total Files: {}", manifest.getTotalFiles());
        logger.info("Total Lines: {}", manifest.getTotalLines());
        logger.info("");
        
        logger.info("Language Distribution:");
        Map<String, Integer> languageStats = manifest.getLanguageStats();
        languageStats.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> 
                    logger.info("  {} - {} lines ({} files)", 
                        entry.getKey(), 
                        entry.getValue(),
                        manifest.getFiles().stream()
                            .filter(f -> f.getLanguage().equals(entry.getKey()))
                            .count())
                );
        logger.info("");
        
        logger.info("Architecture: {}", manifest.getContext().getDetectedArchitecture());
        if (!manifest.getContext().getFrameworks().isEmpty()) {
            logger.info("Frameworks: {}", String.join(", ", manifest.getContext().getFrameworks()));
        }
        if (!manifest.getContext().getTechnologies().isEmpty()) {
            logger.info("Technologies:");
            manifest.getContext().getTechnologies().forEach((key, value) -> 
                logger.info("  {} - {}", key, value)
            );
        }
        logger.info("Git History: {}", manifest.getContext().hasGitHistory() ? "Available" : "Not available");
        logger.info("");
        
        logger.info("Top 10 Largest Files:");
        List<FileInfo> topFiles = manifest.getFiles().stream()
                .sorted(Comparator.comparingInt(FileInfo::getLinesOfCode).reversed())
                .limit(10)
                .collect(Collectors.toList());
        
        for (int i = 0; i < topFiles.size(); i++) {
            FileInfo file = topFiles.get(i);
            Path relativePath = manifest.getRepositoryPath().relativize(file.getFilePath());
            logger.info("  {}. {} - {} lines ({})", 
                i + 1, 
                relativePath, 
                file.getLinesOfCode(), 
                file.getLanguage());
        }
    }

    private void printDependencyGraph(DependencyGraph graph) {
        Graph<PackageNode, PackageEdge> packages = graph.getPackageGraph();
        logger.info("");
        logger.info("Dependency Graph");
        logger.info("----------------");
        logger.info("Files in graph: {} (tests excluded: {}, other languages not parsed: {})",
                graph.getFileGraph().vertexSet().size(), graph.getExcludedTestFiles(), graph.getUnsupportedFiles());
        logger.info("File dependencies: {}", graph.getFileGraph().edgeSet().size());
        logger.info("Packages: {} at depth {}{}", packages.vertexSet().size(), graph.getGroupingDepth(),
                graph.getStrippedPrefixes().isEmpty() ? ""
                        : " (common prefix " + String.join(", ", graph.getStrippedPrefixes()) + " stripped)");
        logger.info("Package dependencies: {}", packages.edgeSet().size());
        logger.info("");

        for (PackageNode node : graph.getPackages()) {
            String targets = packages.outgoingEdgesOf(node).stream()
                    .sorted(Comparator.comparingInt(PackageEdge::getImportCount).reversed())
                    .map(e -> packages.getEdgeTarget(e).getName() + "(" + e.getImportCount() + ")")
                    .collect(Collectors.joining(" "));
            logger.info("  {} [{} files, {} lines, {} internal]{}", node.getName(), node.getFiles().size(),
                    node.getTotalLines(), node.getInternalDependencies(), targets.isEmpty() ? "" : " -> " + targets);
        }
    }

    @Command(name = "version", description = "Show version information")
    public Integer version() {
        logger.info("Deep Documentation Agent v1.0.0");
        logger.info("Built for autonomous repository documentation generation");
        return 0;
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DeepDocCLI()).execute(args);
        System.exit(exitCode);
    }
}
