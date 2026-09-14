package com.consensus.app.ui.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consensus.app.data.local.AppSettings
import com.consensus.app.data.local.SecurePrefs
import com.consensus.app.data.local.SettingsStore
import com.consensus.app.data.local.entity.ExchangeEntity
import com.consensus.app.data.repository.ConversationRepository
import com.consensus.app.domain.ProviderId
import com.consensus.app.domain.RunOptions
import com.consensus.app.domain.RunProgress
import com.consensus.app.domain.RunTranscript
import com.consensus.app.domain.Tier
import com.consensus.app.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the user picked for the next question. Starts from the Settings defaults. */
data class QuestionOptions(
    val included: Set<ProviderId>,
    val tiers: Map<ProviderId, Tier>,
    val judge: ProviderId,
    val rounds: Int,
    val stopWhenAgreed: Boolean,
    val webSearch: Boolean,
) {
    companion object {
        fun from(s: AppSettings, configured: Set<ProviderId>): QuestionOptions {
            val included = ProviderId.values().filter { it in configured && (s.enabled[it] ?: true) }.toSet()
            val judge = if (s.defaultJudge in included) s.defaultJudge else included.firstOrNull() ?: s.defaultJudge
            return QuestionOptions(
                included = included,
                tiers = s.defaultTier,
                judge = judge,
                rounds = s.defaultRounds,
                stopWhenAgreed = s.stopWhenAgreed,
                webSearch = s.webSearch,
            )
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ThreadViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ConversationRepository,
    private val settingsStore: SettingsStore,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _threadId = MutableStateFlow(savedStateHandle.get<Long>("id")?.takeIf { it != Routes.NEW_THREAD_ID })
    val threadId: StateFlow<Long?> = _threadId

    val exchanges: StateFlow<List<ExchangeEntity>> = _threadId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.exchanges(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val title: StateFlow<String> = _threadId
        .flatMapLatest { id -> if (id == null) flowOf("New question") else repository.thread(id).map { it?.title ?: "" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val progress: StateFlow<Map<Long, RunProgress>> = repository.progress
    val configured: StateFlow<Set<ProviderId>> = securePrefs.configured

    private val _settings = MutableStateFlow(AppSettings.DEFAULT)
    val settings: StateFlow<AppSettings> = _settings

    private val _options = MutableStateFlow<QuestionOptions?>(null)
    val options: StateFlow<QuestionOptions?> = _options

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _settings.value = s
                if (_options.value == null) _options.value = QuestionOptions.from(s, securePrefs.configured.value)
            }
        }
    }

    fun updateOptions(transform: (QuestionOptions) -> QuestionOptions) {
        _options.value = _options.value?.let(transform)
    }

    fun clearError() { _error.value = null }

    fun transcript(e: ExchangeEntity): RunTranscript? = repository.parseTranscript(e)

    fun cancel(exchangeId: Long) = repository.cancel(exchangeId)

    fun deleteExchange(id: Long) {
        viewModelScope.launch { repository.deleteExchange(id) }
    }

    fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty()) return
        val opts = _options.value ?: return
        val s = _settings.value
        val configuredNow = securePrefs.configured.value
        val included = opts.included.filter { it in configuredNow }
        if (included.isEmpty()) {
            _error.value = "Select at least one provider that has an API key (see Settings)."
            return
        }
        val judge = if (opts.judge in included) opts.judge else included.first()
        val runOptions = RunOptions(
            models = included.associateWith { p -> s.modelId(p, opts.tiers[p] ?: Tier.BALANCED) },
            judge = judge,
            maxRounds = opts.rounds.coerceIn(0, 3),
            stopWhenAgreed = opts.stopWhenAgreed,
            webSearch = opts.webSearch,
        )
        viewModelScope.launch {
            try {
                val (tid, _) = repository.ask(_threadId.value, q, runOptions)
                _threadId.value = tid
            } catch (e: Exception) {
                _error.value = e.message ?: e.javaClass.simpleName
            }
        }
    }
}
