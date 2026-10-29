package com.deepdoc.analyzer;

import com.deepdoc.config.AnalysisSection;
import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.RepositoryManifest;
import com.deepdoc.repository.GitInterface;
import com.deepdoc.utils.LanguageDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class RepositoryAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryAnalyzer.class);
    
    private static final Set<String> ALWAYS_IGNORE_DIRS = Set.of(".git");

    private final LanguageDetector languageDetector;
    private final Set<String> ignoreDirectories;
    private final Set<String> ignoreExtensions;
    private final Set<String> customIgnoreDirs;
    private final boolean gitEnabled;
    private final AnalysisSection analysis;

    public RepositoryAnalyzer() {
        this(ConfigLoader.load());
    }

    public RepositoryAnalyzer(AppConfig config) {
        this.languageDetector = new LanguageDetector();
        this.ignoreDirectories = new HashSet<>(ALWAYS_IGNORE_DIRS);
        this.ignoreDirectories.addAll(config.getAnalysis().getIgnoreDirectories());
        this.ignoreExtensions = new HashSet<>(config.getAnalysis().getIgnoreExtensions());
        this.customIgnoreDirs = new HashSet<>();
        this.gitEnabled = config.getGit().isEnabled();
        this.analysis = config.getAnalysis();
    }

    public RepositoryManifest analyze(Path repositoryPath) throws IOException {
        logger.info("Starting repository analysis for: {}", repositoryPath);
        
        if (!Files.exists(repositoryPath) || !Files.isDirectory(repositoryPath)) {
            throw new IOException("Repository path does not exist or is not a directory: " + repositoryPath);
        }

        RepositoryManifest manifest = new RepositoryManifest(repositoryPath);
        
        loadGitignore(repositoryPath);
        
        discoverFiles(repositoryPath, manifest);
        
        detectArchitectureAndFrameworks(manifest);
        
        enrichWithGitData(repositoryPath, manifest);
        
        logger.info("Repository analysis completed: {} files, {} total lines", 
                    manifest.getTotalFiles(), manifest.getTotalLines());
        
        return manifest;
    }

    private void loadGitignore(Path repositoryPath) throws IOException {
        Path gitignorePath = repositoryPath.resolve(".gitignore");
        if (Files.exists(gitignorePath)) {
            List<String> lines = Files.readAllLines(gitignorePath);
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    if (line.endsWith("/")) {
                        customIgnoreDirs.add(line.substring(0, line.length() - 1));
                    }
                }
            }
            logger.debug("Loaded .gitignore with {} directory patterns", customIgnoreDirs.size());
        }
    }

    private void discoverFiles(Path repositoryPath, RepositoryManifest manifest) throws IOException {
        Files.walkFileTree(repositoryPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                String dirName = dir.getFileName().toString();
                
                if (ignoreDirectories.contains(dirName) || customIgnoreDirs.contains(dirName)) {
                    logger.debug("Skipping ignored directory: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String fileName = file.getFileName().toString();
                
                if (shouldIgnoreFile(fileName)) {
                    return FileVisitResult.CONTINUE;
                }
                
                try {
                    String language = languageDetector.detectLanguage(file);
                    if (language != null) {
                        int linesOfCode = countLines(file);
                        LocalDateTime lastModified = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis()),
                                ZoneId.systemDefault()
                        );
                        
                        FileInfo fileInfo = new FileInfo(file, language, linesOfCode, lastModified);
                        manifest.addFile(fileInfo);
                        
                        logger.debug("Discovered file: {} ({})", file, language);
                    }
                } catch (IOException e) {
                    logger.warn("Failed to process file {}: {}", file, e.getMessage());
                }
                
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.warn("Failed to visit file: {}", file);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean shouldIgnoreFile(String fileName) {
        for (String ext : ignoreExtensions) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** Counts lines on raw bytes so non-UTF-8 files neither fail nor leak a file handle. */
    private int countLines(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length == 0) {
            return 0;
        }
        int lines = 0;
        for (byte b : bytes) {
            if (b == '\n') {
                lines++;
            }
        }
        return bytes[bytes.length - 1] == '\n' ? lines : lines + 1;
    }

    private void detectArchitectureAndFrameworks(RepositoryManifest manifest) {
        new ArchitectureDetector(analysis).detect(manifest);
    }

    private void enrichWithGitData(Path repositoryPath, RepositoryManifest manifest) {
        if (!gitEnabled) {
            logger.info("Git analysis disabled in application.yml");
            manifest.getContext().setHasGitHistory(false);
            return;
        }

        try {
            GitInterface gitInterface = new GitInterface(repositoryPath);
            
            if (gitInterface.hasGitHistory()) {
                manifest.getContext().setHasGitHistory(true);
                int totalCommits = gitInterface.getTotalCommitCount();
                logger.info("Repository has Git history with {} commits", totalCommits);
            } else {
                logger.info("Repository has no Git history");
            }
            
            gitInterface.close();
        } catch (IOException e) {
            logger.info("Repository is not a Git repository or has no history");
            manifest.getContext().setHasGitHistory(false);
        }
    }
}
