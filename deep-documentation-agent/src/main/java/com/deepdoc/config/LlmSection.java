package com.deepdoc.config;

/**
 * The {@code llm} section of {@code application.yml}. The API key is read from the environment
 * ({@link #apiKeyEnv}), never from this file and never from the command line.
 */
public class LlmSection {
    private boolean enabled = true;
    private String provider = "openai";
    private String model = "gpt-4o-mini";
    private int maxTokens = 200;
    private double temperature = 0.2;
    /** Environment variable that holds the secret. */
    private String apiKeyEnv = "OPENAI_API_KEY";
    private String baseUrl = "https://api.openai.com/v1";
    private int timeoutSeconds = 20;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public String getApiKeyEnv() {
        return apiKeyEnv;
    }

    public void setApiKeyEnv(String apiKeyEnv) {
        this.apiKeyEnv = apiKeyEnv == null || apiKeyEnv.isEmpty() ? "OPENAI_API_KEY" : apiKeyEnv;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl == null || baseUrl.isEmpty() ? "https://api.openai.com/v1" : trimSlash(baseUrl);
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
