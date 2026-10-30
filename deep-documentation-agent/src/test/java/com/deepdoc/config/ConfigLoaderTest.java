package com.deepdoc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void shouldLoadApplicationYmlFromResources() {
        AppConfig config = ConfigLoader.load();

        assertThat(config.getApp().getName()).isEqualTo("Deep Documentation Agent");
        assertThat(config.getApp().getVersion()).isEqualTo("1.0.0");
        assertThat(config.getAnalysis().getIgnoreDirectories())
                .contains("node_modules", "target", ".git");
        assertThat(config.getAnalysis().getIgnoreExtensions())
                .contains(".class", ".jar", ".min.js");
        assertThat(config.getAnalysis().getArchitectureMinConfidence()).isEqualTo(0.3);
        assertThat(config.getGit().isEnabled()).isTrue();
        assertThat(config.getGit().getHalfLifeDays()).isEqualTo(180);
        assertThat(config.getGraph().isIncludeTests()).isFalse();
        assertThat(config.getRanking().getDampingFactor()).isEqualTo(0.85);
        assertThat(config.getRanking().getDeepFraction()).isEqualTo(0.2);
        assertThat(config.getOutput().getDefaultDirectory()).isEqualTo("docs");
        assertThat(config.getLlm().getProvider()).isEqualTo("openai");
        assertThat(config.getLlm().getModel()).isEqualTo("gpt-4o-mini");
        assertThat(config.getLlm().getMaxTokens()).isEqualTo(200);
        assertThat(config.getLlm().getApiKeyEnv()).isEqualTo("OPENAI_API_KEY");
        assertThat(config.getLlm().getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.getLlm().getTimeoutSeconds()).isEqualTo(20);
        assertThat(config.getUnderstand().getMaxFiles()).isEqualTo(40);
        assertThat(config.getUnderstand().isIncludeListed()).isFalse();
    }
}
