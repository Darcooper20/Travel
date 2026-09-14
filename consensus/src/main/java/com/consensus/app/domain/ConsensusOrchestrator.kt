package com.consensus.app.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the three-stage pipeline for one question:
 *  0. fan the question out to every selected provider in parallel;
 *  1..n. critique rounds: each provider sees the others' latest answers,
 *        critiques them and revises its own, ending with a CONSENSUS line;
 *  final. the chosen judge writes one unified answer plus a list of the
 *        disagreements that remain.
 *
 * A provider that fails in a stage is dropped from later stages (its error
 * is kept in the transcript). If the judge failed earlier, the first
 * provider that still has an answer takes over as judge.
 */
@Singleton
class ConsensusOrchestrator @Inject constructor(
    private val clients: Map<ProviderId, @JvmSuppressWildcards LlmClient>,
) {
    class KeyLookup(val get: (ProviderId) -> String?)

    suspend fun run(
        question: String,
        history: List<ChatTurn>,
        options: RunOptions,
        keys: KeyLookup,
        onProgress: (RunProgress) -> Unit,
    ): RunTranscript {
        val providers = options.models.keys.toList()
        if (providers.isEmpty()) throw LlmException("No providers selected")
        val others = { p: ProviderId -> providers.filter { it != p } }
        var total = TokenUsage()
        val rounds = mutableListOf<DebateRound>()

        // ---- Stage 0: independent answers -------------------------------
        val initial = runStage(providers, "Asking ${providers.size} models", onProgress) { p ->
            val model = options.models.getValue(p)
            call(
                p, model, keys,
                LlmRequest(
                    model = model,
                    system = Prompts.answerSystem(p, model, others(p), options.webSearch),
                    turns = history + ChatTurn(ChatTurn.Role.USER, question),
                    webSearch = options.webSearch,
                    maxOutputTokens = ANSWER_TOKENS,
                ),
            )
        }
        rounds += DebateRound(0, initial)
        total = initial.fold(total) { acc, a -> acc + a.usage }
        var latest = initial.filter { it.ok }
        if (latest.isEmpty()) {
            throw LlmException(
                "Every provider failed:\n" + initial.joinToString("\n") { "- ${it.provider.displayName}: ${it.error}" },
            )
        }

        // ---- Stages 1..n: critique and revise -----------------------------
        var consensus: Boolean? = null
        if (latest.size >= 2) {
            for (round in 1..options.maxRounds) {
                val alive = latest.map { it.provider }
                val previous = latest
                val revised = runStage(alive, "Debate round $round of ${options.maxRounds}", onProgress) { p ->
                    val model = options.models.getValue(p)
                    val self = previous.first { it.provider == p }
                    val block = Prompts.otherAnswersBlock(question, self, previous.filter { it.provider != p })
                    call(
                        p, model, keys,
                        LlmRequest(
                            model = model,
                            system = Prompts.critiqueSystem(p, model, round, options.webSearch),
                            turns = history + ChatTurn(ChatTurn.Role.USER, block),
                            webSearch = options.webSearch,
                            maxOutputTokens = CRITIQUE_TOKENS,
                        ),
                    ).let { it.copy(agrees = parseConsensus(it.text)) }
                }
                rounds += DebateRound(round, revised)
                total = revised.fold(total) { acc, a -> acc + a.usage }
                val ok = revised.filter { it.ok }
                if (ok.isEmpty()) break // keep the previous round's answers for the judge
                latest = ok
                consensus = ok.all { it.agrees == true }
                if (consensus && options.stopWhenAgreed) break
            }
        }

        // ---- Final: judge --------------------------------------------------
        val judgeId = if (latest.any { it.provider == options.judge }) options.judge else latest.first().provider
        val judgeModel = options.models.getValue(judgeId)
        onProgress(RunProgress("${judgeId.displayName} is writing the unified answer", mapOf(judgeId to RunProgress.ProviderState.RUNNING)))
        val finals = latest.map { a -> a.copy(text = extractRevisedAnswer(a.text)) }
        val judge = call(
            judgeId, judgeModel, keys,
            LlmRequest(
                model = judgeModel,
                system = Prompts.judgeSystem(judgeId, judgeModel, latest.map { it.provider.displayName }),
                turns = history + ChatTurn(ChatTurn.Role.USER, Prompts.judgeBlock(question, finals)),
                webSearch = false,
                maxOutputTokens = JUDGE_TOKENS,
            ),
        )
        total += judge.usage
        if (!judge.ok) throw LlmException("Judge (${judgeId.displayName}) failed: ${judge.error}")

        return RunTranscript(
            options = options,
            rounds = rounds,
            judge = judge,
            consensusReached = consensus,
            totalUsage = total,
        )
    }

    private suspend fun runStage(
        providers: List<ProviderId>,
        stage: String,
        onProgress: (RunProgress) -> Unit,
        block: suspend (ProviderId) -> ProviderAnswer,
    ): List<ProviderAnswer> = coroutineScope {
        val states = java.util.concurrent.ConcurrentHashMap<ProviderId, RunProgress.ProviderState>()
        providers.forEach { states[it] = RunProgress.ProviderState.RUNNING }
        val publish = { onProgress(RunProgress(stage, states.toMap())) }
        publish()
        providers.map { p ->
            async {
                val answer = block(p)
                states[p] = if (answer.ok) RunProgress.ProviderState.DONE else RunProgress.ProviderState.FAILED
                publish()
                answer
            }
        }.map { it.await() }
    }

    /** Never throws: failures become a ProviderAnswer with `error` set. */
    private suspend fun call(p: ProviderId, model: String, keys: KeyLookup, req: LlmRequest): ProviderAnswer {
        val start = System.currentTimeMillis()
        val client = clients[p] ?: return ProviderAnswer(p, model, error = "no client for ${p.displayName}")
        return try {
            val key = keys.get(p).orEmpty()
            val r = withTimeout(CALL_TIMEOUT_MS) { client.complete(key, req) }
            ProviderAnswer(p, model, text = r.text, citations = r.citations, usage = r.usage, durationMs = System.currentTimeMillis() - start)
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            ProviderAnswer(p, model, error = "${p.displayName}: timed out after ${CALL_TIMEOUT_MS / 1000}s", durationMs = System.currentTimeMillis() - start)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ProviderAnswer(p, model, error = e.message ?: e.javaClass.simpleName, durationMs = System.currentTimeMillis() - start)
        }
    }

    companion object {
        const val ANSWER_TOKENS = 4096
        const val CRITIQUE_TOKENS = 6144
        const val JUDGE_TOKENS = 6144
        const val CALL_TIMEOUT_MS = 300_000L

        fun parseConsensus(text: String?): Boolean? {
            if (text == null) return null
            val line = text.lines().lastOrNull { it.contains("CONSENSUS:", ignoreCase = true) } ?: return null
            val after = line.substringAfter(":", "").trim().uppercase()
            return when {
                after.startsWith("AGREE") -> true
                after.startsWith("DISAGREE") -> false
                else -> null
            }
        }

        /** In critique rounds the model writes "## Critique ... ## Revised answer ...". Keep only the revised part for the judge. */
        fun extractRevisedAnswer(text: String?): String? {
            if (text == null) return null
            val idx = text.indexOf("## Revised answer", ignoreCase = true)
            if (idx < 0) return text
            return text.substring(idx + "## Revised answer".length).trim()
        }
    }
}
