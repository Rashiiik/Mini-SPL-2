package com.smartbudget.pattern.adapter;

import java.util.Optional;

/**
 * The application's own view of "ask a model a question".
 *
 * <p><b>Problem it solves:</b> Groq speaks an OpenAI-shaped JSON protocol —
 * nested {@code choices[0].message.content}, base64 image parts, bearer tokens,
 * HTTP status codes. If the categorisation, anomaly and receipt features each
 * spoke that protocol directly, changing provider would mean editing every one
 * of them, and none could be tested without a network.
 *
 * <p><b>Why Adapter:</b> it converts an external interface into the one this
 * application actually wants. Callers ask for text and get {@link Optional}
 * text. Switching to another provider, or to a local model, means writing one
 * new implementation of this interface.
 *
 * <p><b>Alternative considered:</b> calling {@code HttpClient} from a shared
 * utility method. That centralises the HTTP but still leaks the provider's JSON
 * shape and error semantics into every caller, and leaves no seam to substitute
 * in tests.
 *
 * <p><b>Contract:</b> implementations never throw. Every failure — no key, a
 * timeout, a rate limit, malformed JSON — comes back as {@link Optional#empty()}.
 * That is what makes graceful degradation a one-line {@code orElseGet} at each
 * call site rather than a try/catch repeated everywhere.
 */
public interface AIProvider {

    /** Whether this provider is configured well enough to be worth calling. */
    boolean isAvailable();

    Optional<String> complete(String systemPrompt, String userPrompt);

    Optional<String> completeWithImage(String prompt, byte[] imageBytes, String mimeType);

    /** Shown in the UI so the user can tell which provider produced a result. */
    String name();
}
