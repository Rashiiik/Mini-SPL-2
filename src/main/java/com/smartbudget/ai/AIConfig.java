package com.smartbudget.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where the AI credentials and model names come from.
 *
 * <p>Resolution order is environment variable, then {@code config.properties} in
 * the working directory. The environment wins so a demo machine can supply a key
 * without editing a file, and {@code config.properties} is git-ignored so a key
 * can never be committed by accident.
 *
 * <p>A missing key is a normal, supported state — not an error. It simply means
 * the application runs on its rule-based paths.
 */
public final class AIConfig {

    private static final String CONFIG_FILE = "config.properties";

    // Model availability varies by Groq account, so these defaults are the ones
    // verified against this project's key rather than the largest on offer. A
    // model the account cannot serve fails silently into the rule-based
    // fallback, which looks like "the AI does nothing" and is hard to diagnose.
    private static final String DEFAULT_TEXT_MODEL = "qwen/qwen3.8-27b";
    private static final String DEFAULT_VISION_MODEL = "qwen/qwen3.8-27b";
    private static final String DEFAULT_BASE_URL = "https://api.groq.com/openai/v1";

    private final String apiKey;
    private final String textModel;
    private final String visionModel;
    private final String baseUrl;

    private AIConfig(String apiKey, String textModel, String visionModel, String baseUrl) {
        this.apiKey = apiKey;
        this.textModel = textModel;
        this.visionModel = visionModel;
        this.baseUrl = baseUrl;
    }

    public static AIConfig load() {
        Properties properties = readConfigFile();
        return new AIConfig(
                resolve("GROQ_API_KEY", "groq.api.key", properties, null),
                resolve("GROQ_TEXT_MODEL", "groq.text.model", properties, DEFAULT_TEXT_MODEL),
                resolve("GROQ_VISION_MODEL", "groq.vision.model", properties, DEFAULT_VISION_MODEL),
                resolve("GROQ_BASE_URL", "groq.base.url", properties, DEFAULT_BASE_URL));
    }

    /** Builds a configuration directly, for tests and for pointing at a stub server. */
    public static AIConfig of(String apiKey, String baseUrl) {
        return new AIConfig(apiKey, DEFAULT_TEXT_MODEL, DEFAULT_VISION_MODEL, baseUrl);
    }

    private static String resolve(String envName, String propertyName,
                                  Properties properties, String fallback) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = properties.getProperty(propertyName);
        if (fromFile != null && !fromFile.isBlank()) {
            return fromFile.trim();
        }
        return fallback;
    }

    private static Properties readConfigFile() {
        Properties properties = new Properties();
        Path path = Path.of(CONFIG_FILE);
        if (!Files.isReadable(path)) {
            return properties;
        }
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            // A malformed or unreadable config file must not stop the application
            // starting; it just means AI features stay switched off.
            return new Properties();
        }
        return properties;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String apiKey() {
        return apiKey;
    }

    public String textModel() {
        return textModel;
    }

    public String visionModel() {
        return visionModel;
    }

    public String baseUrl() {
        return baseUrl;
    }
}
