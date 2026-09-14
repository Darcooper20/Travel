package com.consensus.app.data.repository

import com.consensus.app.data.local.SecurePrefs
import com.consensus.app.data.local.dao.ExchangeDao
import com.consensus.app.data.local.dao.ThreadDao
import com.consensus.app.data.local.entity.ExchangeEntity
import com.consensus.app.data.local.entity.ThreadEntity
import com.consensus.app.domain.ChatTurn
import com.consensus.app.domain.ConsensusOrchestrator
import com.consensus.app.domain.RunOptions
import com.consensus.app.domain.RunProgress
import com.consensus.app.domain.RunTranscript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns threads/exchanges and runs questions in an application-scoped
 * coroutine scope, so a run keeps going while the user navigates around
 * the app (it does still die with the process; a RUNNING row found at
 * startup is marked as interrupted).
 */
@Singleton
class ConversationRepository @Inject constructor(
    private val threadDao: ThreadDao,
    private val exchangeDao: ExchangeDao,
    private val orchestrator: ConsensusOrchestrator,
    private val securePrefs: SecurePrefs,
    private val json: Json,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<Long, Job>()

    private val _progress = MutableStateFlow<Map<Long, RunProgress>>(emptyMap())
    /** exchangeId -> live progress for runs in flight. */
    val progress: StateFlow<Map<Long, RunProgress>> = _progress

    init {
        scope.launch { exchangeDao.failAllRunning("Interrupted: the app was closed while this question was running.") }
    }

    fun threads(): Flow<List<ThreadEntity>> = threadDao.observeAll()
    fun thread(id: Long): Flow<ThreadEntity?> = threadDao.observe(id)
    fun exchanges(threadId: Long): Flow<List<ExchangeEntity>> = exchangeDao.observeForThread(threadId)

    suspend fun deleteThread(id: Long) = threadDao.delete(id)
    suspend fun deleteExchange(id: Long) = exchangeDao.delete(id)

    fun parseTranscript(e: ExchangeEntity): RunTranscript? =
        e.transcriptJson?.let { runCatching { json.decodeFromString(RunTranscript.serializer(), it) }.getOrNull() }

    /**
     * Creates the thread if [threadId] is null, records the question as
     * RUNNING and starts the pipeline. Returns (threadId, exchangeId).
     */
    suspend fun ask(threadId: Long?, question: String, options: RunOptions): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val tid = threadId ?: threadDao.insert(
            ThreadEntity(title = question.trim().lines().first().take(80), createdAt = now, updatedAt = now),
        )
        threadDao.touch(tid, now)
        val history = exchangeDao.completedForThread(tid).flatMap { prev ->
            listOfNotNull(
                ChatTurn(ChatTurn.Role.USER, prev.question),
                prev.finalAnswer?.let { ChatTurn(ChatTurn.Role.ASSISTANT, it) },
            )
        }
        val eid = exchangeDao.insert(
            ExchangeEntity(threadId = tid, question = question.trim(), status = ExchangeEntity.RUNNING, createdAt = now),
        )
        _progress.update { it + (eid to RunProgress("Starting")) }
        jobs[eid] = scope.launch {
            val result = runCatching {
                orchestrator.run(
                    question = question.trim(),
                    history = history,
                    options = options,
                    keys = ConsensusOrchestrator.KeyLookup { securePrefs.apiKey(it) },
                    onProgress = { p -> _progress.update { it + (eid to p) } },
                )
            }
            val row = exchangeDao.get(eid)
            if (row != null) {
                result.fold(
                    onSuccess = { t ->
                        exchangeDao.update(
                            row.copy(
                                status = ExchangeEntity.DONE,
                                finalAnswer = t.judge?.text,
                                transcriptJson = json.encodeToString(RunTranscript.serializer(), t),
                            ),
                        )
                    },
                    onFailure = { e ->
                        exchangeDao.update(row.copy(status = ExchangeEntity.ERROR, error = e.message ?: e.javaClass.simpleName))
                    },
                )
                threadDao.touch(tid, System.currentTimeMillis())
            }
            _progress.update { it - eid }
            jobs.remove(eid)
        }
        return tid to eid
    }

    fun cancel(exchangeId: Long) {
        jobs[exchangeId]?.cancel()
        scope.launch {
            exchangeDao.get(exchangeId)?.let { row ->
                if (row.status == ExchangeEntity.RUNNING) {
                    exchangeDao.update(row.copy(status = ExchangeEntity.ERROR, error = "Cancelled."))
                }
            }
            _progress.update { it - exchangeId }
        }
    }
}
