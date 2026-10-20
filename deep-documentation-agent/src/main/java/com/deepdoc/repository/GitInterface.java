package com.deepdoc.repository;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GitInterface {
    private static final Logger logger = LoggerFactory.getLogger(GitInterface.class);
    
    private final Repository repository;
    private final Git git;

    public GitInterface(Path repositoryPath) throws IOException {
        File gitDir = new File(repositoryPath.toFile(), ".git");
        
        if (!gitDir.exists()) {
            throw new IOException("Not a Git repository: " + repositoryPath);
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        this.repository = builder.setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build();
        this.git = new Git(repository);
        
        logger.info("Initialized Git interface for repository: {}", repositoryPath);
    }

    public boolean hasGitHistory() {
        try {
            Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
            return commits.iterator().hasNext();
        } catch (GitAPIException e) {
            logger.warn("Failed to check Git history: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Integer> getFileChurnStats(int maxMonths) {
        Map<String, Integer> churnMap = new HashMap<>();
        
        try {
            Iterable<RevCommit> commits = git.log().call();
            
            for (RevCommit commit : commits) {
                if (commit.getParentCount() > 0) {
                    RevCommit parent = commit.getParent(0);
                    try {
                        List<String> modifiedFiles = getModifiedFiles(parent, commit);
                        for (String file : modifiedFiles) {
                            churnMap.merge(file, 1, Integer::sum);
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to process commit {}: {}", commit.getId().name(), e.getMessage());
                    }
                }
            }
            
            logger.info("Calculated churn stats for {} files", churnMap.size());
        } catch (GitAPIException e) {
            logger.warn("Failed to retrieve Git history for churn analysis: {}", e.getMessage());
        }
        
        return churnMap;
    }

    private List<String> getModifiedFiles(RevCommit parent, RevCommit commit) {
        List<String> modifiedFiles = new ArrayList<>();
        try {
            org.eclipse.jgit.diff.DiffFormatter formatter = new org.eclipse.jgit.diff.DiffFormatter(
                    org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE);
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            
            List<org.eclipse.jgit.diff.DiffEntry> diffs = formatter.scan(parent.getTree(), commit.getTree());
            for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                modifiedFiles.add(diff.getNewPath());
            }
            formatter.close();
        } catch (IOException e) {
            logger.debug("Failed to get modified files: {}", e.getMessage());
        }
        return modifiedFiles;
    }

    public int getTotalCommitCount() {
        try {
            int count = 0;
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                count++;
            }
            return count;
        } catch (GitAPIException e) {
            logger.warn("Failed to count commits: {}", e.getMessage());
            return 0;
        }
    }

    public List<String> getRecentCommitMessages(int maxCount) {
        List<String> messages = new ArrayList<>();
        try {
            Iterable<RevCommit> commits = git.log().setMaxCount(maxCount).call();
            for (RevCommit commit : commits) {
                messages.add(commit.getFullMessage());
            }
        } catch (GitAPIException e) {
            logger.warn("Failed to retrieve commit messages: {}", e.getMessage());
        }
        return messages;
    }

    public void close() {
        if (git != null) {
            git.close();
        }
        if (repository != null) {
            repository.close();
        }
        logger.debug("Closed Git interface");
    }
}
