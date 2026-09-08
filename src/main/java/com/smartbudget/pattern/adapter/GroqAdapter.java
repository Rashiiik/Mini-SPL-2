package com.smartbudget.pattern.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartbudget.ai.AIConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Adapts Groq's OpenAI-compatible chat API to {@link AIProvider}.
 *
 * <p>Uses the JDK's own {@link HttpClient} rather than a vendor SDK, so the only
 * dependency is Jackson for JSON. Everything provider-specific — the endpoint
 * path, the bearer header, the {@code choices[0].message.content} shape, the
 * base64 image part format — is confined to this class.
 *
 * <p>Never throws: every failure becomes {@link Optional#empty()} so callers can
 * fall back without a try/catch. The timeout is deliberately short, because a
 * budgeting app that hangs for thirty seconds on a network stall is worse than
 * one that quietly categorises by keyword.
 */
public class GroqAdapter implements AIProvider {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(12);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final double TEMPERATURE = 0.2;

    private final AIConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Created on first use rather than in the constructor.
     *
     * <p>{@code HttpClient.build()} opens a selector, and on a locked-down or
     * sandboxed machine that throws {@link java.io.UncheckedIOException} — which
     * would escape the constructor and take the whole application down before a
     * window ever opened. Building it lazily keeps every failure inside
     * {@link #send}, where the contract says it becomes an empty result.
     */
    private HttpClient httpClient;

    public GroqAdapter(AIConfig config) {
        this.config = config;
    }

    private HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(CONNECT_TIMEOUT)
                    .build();
        }
        return httpClient;
    }

    @Override
    public boolean isAvailable() {
        return config.hasApiKey();
    }

    @Override
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.textModel());
        body.put("temperature", TEMPERATURE);

        ArrayNode messages = body.putArray("messages");
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", systemPrompt);
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        return send(body);
    }

    @Override
    public Optional<String> completeWithImage(String prompt, byte[] imageBytes, String mimeType) {
        if (!isAvailable() || imageBytes == null || imageBytes.length == 0) {
            return Optional.empty();
        }
        String dataUri = "data:" + (mimeType == null ? "image/jpeg" : mimeType) + ";base64,"
                + Base64.getEncoder().encodeToString(imageBytes);

        ObjectNode body = mapper.createObjectNode();
        body.put("model", config.visionModel());
        body.put("temperature", TEMPERATURE);

        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");

        // The vision endpoint takes content as an array of typed parts rather
        // than a plain string — the main shape difference from a text call.
        ArrayNode parts = user.putArray("content");
        ObjectNode textPart = parts.addObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        ObjectNode imagePart = parts.addObject();
        imagePart.put("type", "image_url");
        imagePart.putObject("image_url").put("url", dataUri);

        return send(body);
    }

    private Optional<String> send(ObjectNode body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.baseUrl() + "/chat/completions"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response =
                    httpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            return extractContent(response.body());

        } catch (InterruptedException e) {
            // Restore the flag so an interrupt is not silently swallowed.
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            // Timeout, DNS failure, TLS problem, malformed JSON, or a sandbox
            // that will not let an HttpClient be created (UncheckedIOException)
            // — all of them mean the same thing to a caller: use the fallback.
            return Optional.empty();
        }
    }

    private Optional<String> extractContent(String responseBody) {
        try {
            JsonNode content = mapper.readTree(responseBody)
                    .path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || !content.isTextual()) {
                return Optional.empty();
            }
            String text = content.asText().trim();
            return text.isEmpty() ? Optional.empty() : Optional.of(text);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public String name() {
        return "Groq (" + config.textModel() + ")";
    }
}
