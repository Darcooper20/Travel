package com.consensus.app.ui.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.consensus.app.data.local.SecurePrefs
import com.consensus.app.data.local.entity.ThreadEntity
import com.consensus.app.data.repository.ConversationRepository
import com.consensus.app.domain.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThreadListViewModel @Inject constructor(
    private val repository: ConversationRepository,
    securePrefs: SecurePrefs,
) : ViewModel() {
    val threads: StateFlow<List<ThreadEntity>> =
        repository.threads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val configuredProviders: StateFlow<Set<ProviderId>> = securePrefs.configured

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteThread(id) }
    }
}
