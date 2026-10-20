package com.deepdoc.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryContextTest {

    @Test
    void shouldInitializeWithDefaultValues() {
        RepositoryContext context = new RepositoryContext();
        
        assertThat(context.getDetectedArchitecture()).isEqualTo("Unknown");
        assertThat(context.getFrameworks()).isEmpty();
        assertThat(context.getTechnologies()).isEmpty();
        assertThat(context.hasGitHistory()).isFalse();
    }

    @Test
    void shouldAddFrameworksWithoutDuplicates() {
        RepositoryContext context = new RepositoryContext();
        
        context.addFramework("Spring Boot");
        context.addFramework("React");
        context.addFramework("Spring Boot");
        
        assertThat(context.getFrameworks())
            .hasSize(2)
            .containsExactly("Spring Boot", "React");
    }

    @Test
    void shouldAddTechnologies() {
        RepositoryContext context = new RepositoryContext();
        
        context.addTechnology("database", "PostgreSQL");
        context.addTechnology("build", "Maven");
        
        assertThat(context.getTechnologies())
            .hasSize(2)
            .containsEntry("database", "PostgreSQL")
            .containsEntry("build", "Maven");
    }

    @Test
    void shouldSetArchitecture() {
        RepositoryContext context = new RepositoryContext();
        
        context.setDetectedArchitecture("Spring Boot Microservices");
        
        assertThat(context.getDetectedArchitecture()).isEqualTo("Spring Boot Microservices");
    }

    @Test
    void shouldSetGitHistoryFlag() {
        RepositoryContext context = new RepositoryContext();
        
        context.setHasGitHistory(true);
        
        assertThat(context.hasGitHistory()).isTrue();
    }
}
