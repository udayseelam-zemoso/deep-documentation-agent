package com.deepdoc.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One independently built unit: a Maven/Gradle module, an npm package, a Go module, and so on.
 * {@code deployable} means it looks like something you can run or ship by itself (a main class,
 * a Dockerfile, a {@code package.json} start script), not just a shared library.
 */
public final class DetectedModule {
    private final String name;
    private final String relativePath;
    private final String buildSystem;
    private final boolean deployable;

    public DetectedModule(String name, String relativePath, String buildSystem, boolean deployable) {
        this.name = name;
        this.relativePath = relativePath;
        this.buildSystem = buildSystem;
        this.deployable = deployable;
    }

    public String getName() {
        return name;
    }

    /** Repository-relative directory, or {@code "."} for the root. */
    public String getRelativePath() {
        return relativePath;
    }

    public String getBuildSystem() {
        return buildSystem;
    }

    public boolean isDeployable() {
        return deployable;
    }

    public Path directory(Path repositoryRoot) {
        return ".".equals(relativePath) ? repositoryRoot : repositoryRoot.resolve(relativePath);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DetectedModule)) return false;
        DetectedModule that = (DetectedModule) o;
        return relativePath.equals(that.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath);
    }

    @Override
    public String toString() {
        return relativePath + "/ (" + buildSystem + (deployable ? ", deployable" : "") + ")";
    }
}
