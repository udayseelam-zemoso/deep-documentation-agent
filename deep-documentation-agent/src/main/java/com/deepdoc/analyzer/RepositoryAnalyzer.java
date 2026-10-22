package com.deepdoc.analyzer;

import com.deepdoc.config.AppConfig;
import com.deepdoc.config.ConfigLoader;
import com.deepdoc.model.FileInfo;
import com.deepdoc.model.RepositoryContext;
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
import java.util.stream.Collectors;

public class RepositoryAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(RepositoryAnalyzer.class);
    
    private static final Set<String> ALWAYS_IGNORE_DIRS = Set.of(".git");

    private final LanguageDetector languageDetector;
    private final Set<String> ignoreDirectories;
    private final Set<String> ignoreExtensions;
    private final Set<String> customIgnoreDirs;
    private final boolean gitEnabled;

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

    private int countLines(Path file) throws IOException {
        long lines = Files.lines(file).count();
        return (int) lines;
    }

    private void detectArchitectureAndFrameworks(RepositoryManifest manifest) {
        RepositoryContext context = manifest.getContext();
        Path repoPath = manifest.getRepositoryPath();
        
        if (detectSpringBoot(manifest)) {
            context.addFramework("Spring Boot");
            context.setDetectedArchitecture("Spring Boot Application");
        }
        
        if (detectReact(repoPath)) {
            context.addFramework("React");
            if (context.getDetectedArchitecture().equals("Unknown")) {
                context.setDetectedArchitecture("React Frontend Application");
            }
        }
        
        if (detectExpress(repoPath)) {
            context.addFramework("Express.js");
            if (context.getDetectedArchitecture().equals("Unknown")) {
                context.setDetectedArchitecture("Express.js Backend");
            }
        }
        
        detectBuildTools(repoPath, context);
        
        logger.info("Detected architecture: {}", context.getDetectedArchitecture());
        logger.info("Detected frameworks: {}", context.getFrameworks());
    }

    private boolean detectSpringBoot(RepositoryManifest manifest) {
        List<FileInfo> javaFiles = manifest.getFiles().stream()
                .filter(f -> "Java".equals(f.getLanguage()))
                .collect(Collectors.toList());
        
        for (FileInfo file : javaFiles) {
            try {
                String content = Files.readString(file.getFilePath());
                if (content.contains("@SpringBootApplication")) {
                    logger.debug("Found Spring Boot application marker in: {}", file.getFilePath());
                    return true;
                }
            } catch (IOException e) {
                logger.debug("Failed to read file {}: {}", file.getFilePath(), e.getMessage());
            }
        }
        
        return false;
    }

    private boolean detectReact(Path repoPath) {
        Path packageJson = repoPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson);
                return content.contains("\"react\"");
            } catch (IOException e) {
                logger.debug("Failed to read package.json: {}", e.getMessage());
            }
        }
        return false;
    }

    private boolean detectExpress(Path repoPath) {
        Path packageJson = repoPath.resolve("package.json");
        if (Files.exists(packageJson)) {
            try {
                String content = Files.readString(packageJson);
                return content.contains("\"express\"");
            } catch (IOException e) {
                logger.debug("Failed to read package.json: {}", e.getMessage());
            }
        }
        return false;
    }

    private void detectBuildTools(Path repoPath, RepositoryContext context) {
        if (Files.exists(repoPath.resolve("pom.xml"))) {
            context.addTechnology("build", "Maven");
        }
        if (Files.exists(repoPath.resolve("build.gradle")) || 
            Files.exists(repoPath.resolve("build.gradle.kts"))) {
            context.addTechnology("build", "Gradle");
        }
        if (Files.exists(repoPath.resolve("package.json"))) {
            context.addTechnology("build", "npm/yarn");
        }
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
