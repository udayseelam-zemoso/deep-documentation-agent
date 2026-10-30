package com.deepdoc.understand;

import com.deepdoc.config.LlmSection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Chat Completions against OpenAI (or a compatible {@code baseUrl}). The key is read from the
 * environment variable named in {@code llm.apiKeyEnv} (default {@code OPENAI_API_KEY}) so it
 * never sits in {@code application.yml} or in argv.
 *
 * <p>Any HTTP or parse failure returns {@code null}; {@link ResponsibilityInferrer} then keeps
 * the heuristic sentence. Documentation still finishes when the model is down.
 */
public final class OpenAiLlmClient implements LlmClient {
    private static final Logger logger = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final LlmSection config;
    private final String apiKey;
    private final HttpClient http;

    public OpenAiLlmClient(LlmSection config) {
        this.config = config;
        String env = config.getApiKeyEnv() == null || config.getApiKeyEnv().isEmpty()
                ? "OPENAI_API_KEY" : config.getApiKeyEnv();
        String key = System.getenv(env);
        this.apiKey = key == null || key.trim().isEmpty() ? null : key.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds())))
                .build();
    }

    @Override
    public boolean available() {
        return apiKey != null;
    }

    @Override
    public String mode() {
        return available() ? "openai" : "heuristic";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!available()) {
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds())))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson(systemPrompt, userPrompt),
                            StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("LLM HTTP {} — using the heuristic responsibility instead", response.statusCode());
                return null;
            }
            return contentOf(response.body());
        } catch (Exception e) {
            logger.warn("LLM call failed — using the heuristic responsibility instead: {}", e.getMessage());
            return null;
        }
    }

    /** Visible for tests: the JSON body, which never includes the API key. */
    String requestJson(String systemPrompt, String userPrompt) {
        JsonObject root = new JsonObject();
        root.addProperty("model", config.getModel());
        root.addProperty("temperature", config.getTemperature());
        root.addProperty("max_tokens", Math.max(32, config.getMaxTokens()));
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));
        root.add("messages", messages);
        return root.toString();
    }

    static String contentOf(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.size() == 0) {
                return null;
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                return null;
            }
            String content = message.get("content").getAsString();
            return content == null || content.trim().isEmpty() ? null : content.trim();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject message(String role, String content) {
        JsonObject object = new JsonObject();
        object.addProperty("role", role);
        object.addProperty("content", content == null ? "" : content);
        return object;
    }
}
