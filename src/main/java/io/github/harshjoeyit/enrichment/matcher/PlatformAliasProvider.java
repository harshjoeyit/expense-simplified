package io.github.harshjoeyit.enrichment.matcher;

import java.util.List;

/**
 * Provider interface to resolve platform-to-counterparty/narration keywords or aliases.
 */
@FunctionalInterface
public interface PlatformAliasProvider {

    /**
     * Returns the list of substrings or keywords used to match against transaction counterparties/narrations.
     * For example, for platform "SWIGGY", this may return ["SWIGGY", "INSTAMART"].
     *
     * @param platform the platform identifier from the order receipt (e.g., "SWIGGY", "AMAZON")
     * @return list of matching aliases/keywords (never null)
     */
    List<String> getAliases(String platform);
}
