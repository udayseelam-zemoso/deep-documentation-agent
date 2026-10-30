package com.deepdoc.understand;

/**
 * Tiny text-completion port. Day 8-10 only needs one sentence per DEEP file. The OpenAI
 * client is one implementation; tests and {@code --no-llm} use {@link #disabled()}.
 */
public interface LlmClient {

    /** False when the user turned the LLM off, or when no API key is in the environment. */
    boolean available();

    /** How the CLI should describe this client: {@code openai}, {@code heuristic}, {@code disabled}. */
    String mode();

    /**
     * One completion, or {@code null} on any failure (timeout, HTTP error, empty body).
     * Implementations must not log the API key or the raw request body.
     */
    String complete(String systemPrompt, String userPrompt);

    static LlmClient disabled() {
        return new LlmClient() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String mode() {
                return "disabled";
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return null;
            }
        };
    }
}
