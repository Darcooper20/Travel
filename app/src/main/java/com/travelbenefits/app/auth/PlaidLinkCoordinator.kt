package com.travelbenefits.app.auth

import com.travelbenefits.app.data.repository.PlaidRepository
import com.travelbenefits.app.domain.model.PlaidItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges the Activity-owned Plaid Link result back to whichever screen started it. */
@Singleton
class PlaidLinkCoordinator @Inject constructor(
    private val plaidRepository: PlaidRepository,
) {
    sealed class LinkState {
        data object Idle : LinkState()
        data object Exchanging : LinkState()
        data class Linked(val item: PlaidItem) : LinkState()
        data class Failed(val message: String) : LinkState()
    }

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    suspend fun onLinkSuccess(publicToken: String, institutionName: String?) {
        _state.value = LinkState.Exchanging
        plaidRepository.completeLink(publicToken, institutionName).fold(
            onSuccess = { _state.value = LinkState.Linked(it) },
            onFailure = { _state.value = LinkState.Failed(it.message ?: "Couldn't finish linking.") },
        )
    }

    fun onLinkExit(message: String?) {
        _state.value = if (message == null) LinkState.Idle else LinkState.Failed(message)
    }

    fun reset() {
        _state.value = LinkState.Idle
    }
}
