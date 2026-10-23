package com.deepdoc.repository;

import java.util.Collections;
import java.util.List;

/**
 * One non-merge commit and the files it touched (paths relative to the Git work tree, with
 * forward slashes). This is all the ranking needs from history: when, and which files.
 */
public final class CommitRecord {
    private final String id;
    private final long commitTimeSeconds;
    private final List<String> paths;

    public CommitRecord(String id, long commitTimeSeconds, List<String> paths) {
        this.id = id;
        this.commitTimeSeconds = commitTimeSeconds;
        this.paths = Collections.unmodifiableList(paths);
    }

    /** Full commit SHA. */
    public String getId() {
        return id;
    }

    /** Committer time in epoch seconds; drives the recency decay. */
    public long getCommitTimeSeconds() {
        return commitTimeSeconds;
    }

    /** Files added or modified by the commit; deleted files are left out since they no longer exist. */
    public List<String> getPaths() {
        return paths;
    }
}
