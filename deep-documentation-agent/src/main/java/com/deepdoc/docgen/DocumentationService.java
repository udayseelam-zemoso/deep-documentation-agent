package com.deepdoc.docgen;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.OutputSection;
import com.deepdoc.graph.DependencyGraph;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.ranker.PackageRank;
import com.deepdoc.ranker.RankingResult;
import com.deepdoc.understand.UnderstandingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Week 3 pipeline: turn the ranking, the outlines and the package graph into Markdown a
 * stranger can read.
 *
 * <p>The output folder (default {@code <repo>/docs}, or {@code --output}) receives:
 * <ul>
 *   <li>{@code README.md} — what the system is, a package diagram, links</li>
 *   <li>{@code architecture.md} — style, modules, evidence, Ca/Ce</li>
 *   <li>{@code packages/<name>.md} — DEEP sections, SUMMARY paragraphs, LISTED table</li>
 * </ul>
 * Every claim that came from the code is a {@code file:line} link back to the source.
 * Generated docs are not committed by this project ({@code docs/} is gitignored).
 */
public class DocumentationService {
    private static final Logger logger = LoggerFactory.getLogger(DocumentationService.class);

    private final OutputSection output;

    public DocumentationService(AppConfig config) {
        this.output = config.getOutput();
    }

    /**
     * Writes the documentation set under {@code outputDir}, creating the directory if needed.
     * Existing generated files with the same names are overwritten; other files in the folder
     * are left alone.
     */
    public DocumentationResult generate(Path outputDir, RepositoryManifest manifest,
                                        DependencyGraph graph, RankingResult ranking,
                                        UnderstandingResult understanding) throws IOException {
        Path root = outputDir.toAbsolutePath().normalize();
        Files.createDirectories(root);

        boolean diagrams = output.isGenerateDiagrams();
        List<Path> written = new ArrayList<>();

        Path readme = root.resolve("README.md");
        write(readme, OverviewPage.render(readme, manifest, graph, ranking, understanding, diagrams));
        written.add(readme);

        Path architecture = root.resolve("architecture.md");
        write(architecture, ArchitecturePage.render(architecture, manifest, graph, ranking, diagrams));
        written.add(architecture);

        int deep = 0;
        int summary = 0;
        int listed = 0;
        Path packagesDir = root.resolve("packages");
        if (!ranking.getPackages().isEmpty()) {
            Files.createDirectories(packagesDir);
        }
        for (PackageRank pack : ranking.getPackages()) {
            Path page = packagesDir.resolve(DocNames.packageFileName(pack.getName()));
            PackagePage rendered = PackagePage.render(page, manifest.getRepositoryPath(), pack,
                    graph, ranking, understanding, diagrams);
            write(page, rendered.markdown);
            written.add(page);
            deep += rendered.deepSections;
            summary += rendered.summarySections;
            listed += rendered.listedEntries;
        }

        logger.info("Wrote {} documentation files to {} ({} package pages, {} deep, {} summary, {} listed)",
                written.size(), root, ranking.getPackages().size(), deep, summary, listed);
        return new DocumentationResult(root, written, ranking.getPackages().size(),
                deep, summary, listed, diagrams);
    }

    /**
     * {@code --output} if the user passed one, otherwise {@code <repo>/<output.defaultDirectory>}.
     * A relative {@code --output} is resolved against the current working directory.
     */
    public static Path resolveOutputDirectory(Path repositoryRoot, String outputOption, OutputSection output) {
        if (outputOption != null && !outputOption.trim().isEmpty()) {
            return Path.of(outputOption.trim()).toAbsolutePath().normalize();
        }
        String folder = output == null || output.getDefaultDirectory() == null
                || output.getDefaultDirectory().trim().isEmpty()
                ? "docs" : output.getDefaultDirectory().trim();
        return repositoryRoot.resolve(folder).toAbsolutePath().normalize();
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }
}
