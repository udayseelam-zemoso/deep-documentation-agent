package com.deepdoc.repository;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Read-only access to a repository's Git history through JGit (no {@code git} binary needed).
 * Used to detect whether history exists and to read the commits behind the activity signal and
 * the co-change edges. Close it after use; it holds the repository open.
 */
public class GitInterface implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(GitInterface.class);
    
    private final Repository repository;
    private final Git git;

    /**
     * Opens the repository containing {@code repositoryPath}. The path may be a sub-directory of the
     * work tree (e.g. one module of a monorepo); Git paths are then relative to {@link #getWorkTree()}.
     */
    public GitInterface(Path repositoryPath) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .findGitDir(repositoryPath.toFile());
        if (builder.getGitDir() == null) {
            throw new IOException("Not a Git repository: " + repositoryPath);
        }
        this.repository = builder.build();
        if (repository.isBare()) {
            repository.close();
            throw new IOException("Bare repositories are not supported: " + repositoryPath);
        }
        this.git = new Git(repository);
        
        logger.info("Initialized Git interface for repository: {}", repository.getWorkTree());
    }

    /** Root of the work tree; commit paths are relative to it. */
    public Path getWorkTree() {
        return repository.getWorkTree().toPath().toAbsolutePath().normalize();
    }

    /** True if the repository has at least one commit. */
    public boolean hasGitHistory() {
        try {
            Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
            return commits.iterator().hasNext();
        } catch (GitAPIException e) {
            logger.warn("Failed to check Git history: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Non-merge commits from the last {@code historyMonths}, newest first, capped at {@code maxCommits}.
     * Commits touching more than {@code bulkCommitFileLimit} files (formatting, renames, vendoring,
     * the initial import) are skipped because they say nothing about where work happens.
     */
    public List<CommitRecord> getCommitHistory(int historyMonths, int maxCommits, int bulkCommitFileLimit) {
        List<CommitRecord> records = new ArrayList<>();
        Date cutoff = Date.from(ZonedDateTime.now().minusMonths(historyMonths).toInstant());
        int skippedBulk = 0;
        try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
             ObjectReader reader = repository.newObjectReader()) {
            formatter.setRepository(repository);
            formatter.setDetectRenames(true);
            Iterable<RevCommit> commits = git.log()
                    .setRevFilter(CommitTimeRevFilter.after(cutoff))
                    .setMaxCount(maxCommits)
                    .call();
            for (RevCommit commit : commits) {
                // Merge commits repeat changes already counted on the merged branch.
                if (commit.getParentCount() > 1) {
                    continue;
                }
                List<DiffEntry> diffs;
                // The root commit has no parent to diff against: compare with an empty tree.
                if (commit.getParentCount() == 0) {
                    diffs = formatter.scan(new EmptyTreeIterator(), new CanonicalTreeParser(null, reader, commit.getTree()));
                } else {
                    diffs = formatter.scan(commit.getParent(0).getTree(), commit.getTree());
                }
                if (diffs.size() > bulkCommitFileLimit) {
                    skippedBulk++;
                    continue;
                }
                List<String> paths = new ArrayList<>();
                for (DiffEntry diff : diffs) {
                    if (diff.getChangeType() != DiffEntry.ChangeType.DELETE) {
                        paths.add(diff.getNewPath());
                    }
                }
                records.add(new CommitRecord(commit.getName(), commit.getCommitTime(), paths));
            }
        } catch (GitAPIException | IOException e) {
            logger.warn("Failed to read Git history: {}", e.getMessage());
        }
        logger.info("Read {} commits from the last {} months ({} bulk commits skipped)",
                records.size(), historyMonths, skippedBulk);
        return records;
    }

    /** Number of commits reachable from HEAD; reported in the repository summary. */
    public int getTotalCommitCount() {
        try {
            int count = 0;
            for (RevCommit ignored : git.log().call()) {
                count++;
            }
            return count;
        } catch (GitAPIException e) {
            logger.warn("Failed to count commits: {}", e.getMessage());
            return 0;
        }
    }

    /** Full messages of the newest commits, kept as context for the documentation step. */
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

    @Override
    public void close() {
        git.close();
        repository.close();
        logger.debug("Closed Git interface");
    }
}
