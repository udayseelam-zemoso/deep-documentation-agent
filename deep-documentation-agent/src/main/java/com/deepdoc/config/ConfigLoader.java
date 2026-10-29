package com.deepdoc.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loads {@code application.yml} from the classpath ({@code src/main/resources}).
 */
public final class ConfigLoader {
    public static final String RESOURCE_PATH = "/application.yml";

    private ConfigLoader() {
    }

    public static AppConfig load() {
        try (InputStream input = ConfigLoader.class.getResourceAsStream(RESOURCE_PATH)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing classpath resource " + RESOURCE_PATH
                                + ". Expected src/main/resources/application.yml");
            }
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new Constructor(AppConfig.class, options));
            AppConfig config = yaml.load(input);
            if (config == null) {
                throw new IllegalStateException(RESOURCE_PATH + " is empty");
            }
            return withDefaults(config);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE_PATH, e);
        }
    }

    private static AppConfig withDefaults(AppConfig config) {
        if (config.getApp() == null) {
            config.setApp(new AppSection());
        }
        if (config.getAnalysis() == null) {
            config.setAnalysis(new AnalysisSection());
        }
        if (config.getGit() == null) {
            config.setGit(new GitSection());
        }
        if (config.getGraph() == null) {
            config.setGraph(new GraphSection());
        }
        if (config.getRanking() == null) {
            config.setRanking(new RankingSection());
        }
        if (config.getOutput() == null) {
            config.setOutput(new OutputSection());
        }
        if (config.getLlm() == null) {
            config.setLlm(new LlmSection());
        }
        if (config.getUnderstand() == null) {
            config.setUnderstand(new UnderstandSection());
        }
        return config;
    }
}
