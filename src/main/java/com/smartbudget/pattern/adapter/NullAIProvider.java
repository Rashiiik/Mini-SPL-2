package com.smartbudget.pattern.adapter;

import java.util.Optional;

/**
 * The provider used when no API key is configured.
 *
 * <p>A Null Object rather than a {@code null} reference: callers never have to
 * check whether AI is configured, because asking this provider simply yields
 * nothing and the rule-based path takes over. That keeps
 * "AI unavailable" and "AI failed" on exactly the same code path, so the
 * fallback is exercised constantly rather than only in a rare error case.
 */
public class NullAIProvider implements AIProvider {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Optional<String> complete(String systemPrompt, String userPrompt) {
        return Optional.empty();
    }

    @Override
    public Optional<String> completeWithImage(String prompt, byte[] imageBytes, String mimeType) {
        return Optional.empty();
    }

    @Override
    public String name() {
        return "None (AI disabled)";
    }
}
