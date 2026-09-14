package com.consensus.app.domain

import kotlinx.serialization.Serializable

/** Everything the user chose for one question (defaults come from Settings). */
@Serializable
data class RunOptions(
    /** Provider -> model ID actually used. Only providers the user included. */
    val models: Map<ProviderId, String>,
    val judge: ProviderId,
    /** Number of critique/revision rounds after the initial answers (0..3). */
    val maxRounds: Int,
    /** Stop the rounds early once every provider reports CONSENSUS: AGREE. */
    val stopWhenAgreed: Boolean,
    val webSearch: Boolean,
)

@Serializable
data class ProviderAnswer(
    val provider: ProviderId,
    val model: String,
    val text: String? = null,
    val citations: List<Citation> = emptyList(),
    val usage: TokenUsage = TokenUsage(),
    val error: String? = null,
    /** Parsed from the trailing CONSENSUS line in critique rounds; null in round 0. */
    val agrees: Boolean? = null,
    val durationMs: Long = 0,
) {
    val ok: Boolean get() = text != null && error == null
}

@Serializable
data class DebateRound(
    /** 0 = initial independent answers, 1.. = critique/revision rounds. */
    val index: Int,
    val answers: List<ProviderAnswer>,
)

@Serializable
data class RunTranscript(
    val options: RunOptions,
    val rounds: List<DebateRound> = emptyList(),
    val judge: ProviderAnswer? = null,
    val consensusReached: Boolean? = null,
    val totalUsage: TokenUsage = TokenUsage(),
)

/** Live progress while a run is executing. */
data class RunProgress(
    val stage: String,
    val providerStates: Map<ProviderId, ProviderState> = emptyMap(),
) {
    enum class ProviderState { WAITING, RUNNING, DONE, FAILED, SKIPPED }
}
