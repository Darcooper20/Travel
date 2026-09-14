package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.remote.awards.AwardSearchProvider
import com.travelbenefits.app.data.remote.awards.ResearchAwardProvider
import com.travelbenefits.app.data.remote.awards.SeatsAeroProvider
import com.travelbenefits.app.domain.model.AwardResult
import com.travelbenefits.app.domain.model.AwardSearchRequest
import com.travelbenefits.app.domain.model.AwardSearchResponse
import com.travelbenefits.app.domain.model.ResultKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Routes searches to the chosen provider and keeps user-entered results alongside, each labelled. */
@Singleton
class AwardSearchRepository @Inject constructor(
    private val seatsAero: SeatsAeroProvider,
    private val research: ResearchAwardProvider,
) {
    val providers: List<AwardSearchProvider> get() = listOf(seatsAero, research)

    private val _manual = MutableStateFlow<List<AwardResult>>(emptyList())
    /** Results the user typed in from another tool or confirmed after booking (session-scoped). */
    val manualResults: StateFlow<List<AwardResult>> = _manual.asStateFlow()

    suspend fun availableProviders(): List<AwardSearchProvider> = providers.filter { it.isAvailable() }

    suspend fun search(providerId: String, request: AwardSearchRequest): Result<AwardSearchResponse> {
        val provider = providers.firstOrNull { it.id == providerId } ?: return Result.failure(IllegalArgumentException("Unknown provider"))
        if (!provider.isAvailable()) return Result.failure(IllegalStateException("${provider.displayName} isn't configured."))
        return provider.search(request)
    }

    fun addManual(result: AwardResult) {
        _manual.value = _manual.value + result.copy(kind = if (result.kind == ResultKind.USER_CONFIRMED) result.kind else ResultKind.USER_ENTERED)
    }

    fun clearManual() { _manual.value = emptyList() }
}
