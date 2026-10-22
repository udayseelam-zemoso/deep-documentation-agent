package com.deepdoc.repository;

import com.deepdoc.testutil.ExampleRepos;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitInterfaceTest {

    @TempDir
    Path repo;

    private static void commit(Git git, String message) throws Exception {
        PersonIdent author = new PersonIdent("Test", "test@example.com");
        git.add().addFilepattern(".").call();
        // Explicit GPG config keeps the developer's global git signing settings out of the test.
        git.commit().setMessage(message).setAuthor(author).setCommitter(author)
                .setSign(false).setGpgConfig(new GpgConfig(new Config())).call();
    }

    @Test
    void readsCommitsSkipsBulkCommitsAndWorksFromSubdirectory() throws Exception {
        try (Git git = Git.init().setDirectory(repo.toFile()).call()) {
            for (int i = 0; i < 5; i++) {
                ExampleRepos.write(repo.resolve("module/bulk/F" + i + ".java"), "class F" + i + " {}");
            }
            commit(git, "initial import");
            ExampleRepos.write(repo.resolve("module/A.java"), "class A {}");
            commit(git, "add A");
            ExampleRepos.write(repo.resolve("module/A.java"), "class A { int x; }");
            ExampleRepos.write(repo.resolve("module/B.java"), "class B {}");
            commit(git, "change A, add B");
        }

        try (GitInterface gitInterface = new GitInterface(repo.resolve("module"))) {
            assertThat(gitInterface.getWorkTree()).isEqualTo(repo.toAbsolutePath().normalize());
            assertThat(gitInterface.hasGitHistory()).isTrue();

            List<CommitRecord> commits = gitInterface.getCommitHistory(24, 100, 3);

            assertThat(commits).hasSize(2);
            assertThat(commits.get(0).getPaths()).containsExactlyInAnyOrder("module/A.java", "module/B.java");
            assertThat(commits.get(1).getPaths()).containsExactly("module/A.java");
        }
    }

    @Test
    void rejectsDirectoryOutsideAnyRepository() {
        assertThatThrownBy(() -> new GitInterface(repo)).isInstanceOf(IOException.class);
    }
}
