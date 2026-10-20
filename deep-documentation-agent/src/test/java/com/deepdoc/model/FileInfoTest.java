package com.deepdoc.model;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FileInfoTest {

    @Test
    void shouldCreateFileInfoWithAllProperties() {
        Path path = Paths.get("src/Example.java");
        LocalDateTime now = LocalDateTime.now();
        
        FileInfo fileInfo = new FileInfo(path, "Java", 150, now);
        
        assertThat(fileInfo.getFilePath()).isEqualTo(path);
        assertThat(fileInfo.getLanguage()).isEqualTo("Java");
        assertThat(fileInfo.getLinesOfCode()).isEqualTo(150);
        assertThat(fileInfo.getLastModified()).isEqualTo(now);
        assertThat(fileInfo.getImportanceScore()).isEqualTo(0.0);
    }

    @Test
    void shouldUpdateImportanceScore() {
        FileInfo fileInfo = new FileInfo(
            Paths.get("Example.java"), 
            "Java", 
            100, 
            LocalDateTime.now()
        );
        
        fileInfo.setImportanceScore(0.85);
        
        assertThat(fileInfo.getImportanceScore()).isEqualTo(0.85);
    }

    @Test
    void shouldImplementEqualsBasedOnPath() {
        Path path = Paths.get("Example.java");
        FileInfo file1 = new FileInfo(path, "Java", 100, LocalDateTime.now());
        FileInfo file2 = new FileInfo(path, "Java", 100, LocalDateTime.now());
        
        assertThat(file1).isEqualTo(file2);
        assertThat(file1.hashCode()).isEqualTo(file2.hashCode());
    }

    @Test
    void shouldNotBeEqualWithDifferentPaths() {
        FileInfo file1 = new FileInfo(
            Paths.get("Example1.java"), 
            "Java", 
            100, 
            LocalDateTime.now()
        );
        FileInfo file2 = new FileInfo(
            Paths.get("Example2.java"), 
            "Java", 
            100, 
            LocalDateTime.now()
        );
        
        assertThat(file1).isNotEqualTo(file2);
    }
}
