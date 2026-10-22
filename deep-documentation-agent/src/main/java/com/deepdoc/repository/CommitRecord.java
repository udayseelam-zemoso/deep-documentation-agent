package com.deepdoc.repository;

import java.util.Collections;
import java.util.List;

/** One non-merge commit and the files it touched (paths relative to the Git work tree). */
public final class CommitRecord {
    private final String id;
    private final long commitTimeSeconds;
    private final List<String> paths;

    public CommitRecord(String id, long commitTimeSeconds, List<String> paths) {
        this.id = id;
        this.commitTimeSeconds = commitTimeSeconds;
        this.paths = Collections.unmodifiableList(paths);
    }

    public String getId() {
        return id;
    }

    public long getCommitTimeSeconds() {
        return commitTimeSeconds;
    }

    public List<String> getPaths() {
        return paths;
    }
}
