package com.consensus.app.domain

import kotlinx.serialization.Serializable

/** The four providers this app fans a question out to. */
@Serializable
enum class ProviderId(val displayName: String, val keyUrl: String) {
    ANTHROPIC("Claude", "https://console.anthropic.com/settings/keys"),
    OPENAI("ChatGPT", "https://platform.openai.com/api-keys"),
    GEMINI("Gemini", "https://aistudio.google.com/apikey"),
    XAI("Grok", "https://console.x.ai/"),
}

/** Cost/capability tier. Each provider has one editable model ID per tier in Settings. */
@Serializable
enum class Tier(val label: String) {
    BEST("Best"),
    BALANCED("Balanced"),
    CHEAP("Cheap"),
}

/**
 * Default model IDs per provider and tier. These are what the app ships with;
 * every one of them is editable in Settings because provider model names
 * change every few months. Verify against each provider's model list.
 */
object DefaultModels {
    fun forProvider(provider: ProviderId, tier: Tier): String = when (provider) {
        ProviderId.ANTHROPIC -> when (tier) {
            Tier.BEST -> "claude-opus-5"
            Tier.BALANCED -> "claude-sonnet-5"
            Tier.CHEAP -> "claude-haiku-4-5-20251001"
        }
        ProviderId.OPENAI -> when (tier) {
            Tier.BEST -> "gpt-5.6-sol"
            Tier.BALANCED -> "gpt-5.6-terra"
            Tier.CHEAP -> "gpt-5.6-luna"
        }
        ProviderId.GEMINI -> when (tier) {
            Tier.BEST -> "gemini-3.1-pro"
            Tier.BALANCED -> "gemini-3.8-flash"
            Tier.CHEAP -> "gemini-3.1-flash-lite"
        }
        ProviderId.XAI -> when (tier) {
            Tier.BEST -> "grok-4.6"
            Tier.BALANCED -> "grok-4.5"
            Tier.CHEAP -> "grok-4.3"
        }
    }
}
