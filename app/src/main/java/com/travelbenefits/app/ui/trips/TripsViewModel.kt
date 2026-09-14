package com.travelbenefits.app.ui.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.TripRepository
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.ui.common.parseUserDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** A trip plus the "is this earning?" verdict computed against the user's loyalty accounts. */
data class TripRowState(
    val trip: Trip,
    val isUpcoming: Boolean,
    val matchedAccount: LoyaltyAccount?,
    /** Human explanation of the earning situation, or null when there's nothing to say. */
    val earningNote: String?,
    val earningIsProblem: Boolean,
)

data class TripsUiState(
    val upcoming: List<TripRowState> = emptyList(),
    val past: List<TripRowState> = emptyList(),
)

data class AddTripState(
    val isOpen: Boolean = false,
    val kind: TripKind = TripKind.HOTEL,
    val provider: String = "",
    val title: String = "",
    val confirmationNumber: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val destination: String = "",
    val program: LoyaltyProgram? = null,
    val loyaltyNumberOnBooking: Boolean = false,
    val notes: String = "",
)

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    loyaltyRepository: LoyaltyRepository,
) : ViewModel() {

    val uiState: StateFlow<TripsUiState> = combine(
        tripRepository.observeTrips(),
        loyaltyRepository.observeAccounts(),
    ) { trips, accounts ->
        val today = LocalDate.now().toEpochDay()
        val byProgram = accounts.associateBy { it.program }
        val rows = trips.map { trip -> toRow(trip, today, byProgram) }
        TripsUiState(
            upcoming = rows.filter { it.isUpcoming }.sortedBy { it.trip.startEpochDay ?: Long.MAX_VALUE },
            past = rows.filterNot { it.isUpcoming }.sortedByDescending { it.trip.startEpochDay ?: 0L },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripsUiState())

    private val _addState = MutableStateFlow(AddTripState())
    val addState: StateFlow<AddTripState> = _addState.asStateFlow()

    private fun toRow(trip: Trip, today: Long, byProgram: Map<LoyaltyProgram, LoyaltyAccount>): TripRowState {
        val program = trip.loyaltyProgram
        val account = program?.let { byProgram[it] }
        val upcoming = trip.isUpcoming(today)
        val state = trip.loyaltyNumberState
        val (note, problem) = when {
            trip.isCancelled -> "Cancelled." to false
            program == null -> "No loyalty program recognised for this booking." to false
            state == com.travelbenefits.app.domain.model.LoyaltyNumberState.CONFIRMED -> "Crediting to ${program.displayName}" + (account?.tier?.let { " ($it)" } ?: "") + "." to false
            state == com.travelbenefits.app.domain.model.LoyaltyNumberState.MISSING && account != null && upcoming -> "Your ${program.displayName} number is not on this booking - add it (#${account.membershipNumber ?: "number not saved"}) before check-in so it earns." to true
            state == com.travelbenefits.app.domain.model.LoyaltyNumberState.MISSING && account != null -> "Booked without your ${program.displayName} number - request retroactive credit if it wasn't earned." to true
            account != null && upcoming -> "Loyalty number status unknown for this ${program.displayName} booking - check the reservation and confirm." to true
            upcoming -> "This would credit to ${program.displayName}; you don't have that account saved yet. Joining is free." to true
            else -> "Loyalty number status unknown (${program.displayName})." to false
        }
        return TripRowState(trip = trip, isUpcoming = upcoming, matchedAccount = account, earningNote = note, earningIsProblem = problem)
    }

    fun openAdd() {
        _addState.value = AddTripState(isOpen = true)
    }

    fun closeAdd() {
        _addState.value = AddTripState(isOpen = false)
    }

    fun updateAdd(transform: (AddTripState) -> AddTripState) {
        val next = transform(_addState.value)
        // Auto-detect the program from the provider as the user types, unless they've picked one.
        val detected = if (next.program == null || next.provider != _addState.value.provider) {
            LoyaltyProgramCatalog.programForProvider(next.provider) ?: next.program
        } else {
            next.program
        }
        _addState.value = next.copy(program = detected)
    }

    fun saveAdd() {
        val s = _addState.value
        if (s.provider.isBlank()) return
        viewModelScope.launch {
            tripRepository.addManual(
                kind = s.kind,
                provider = s.provider.trim(),
                title = s.title.trim().ifBlank { s.provider.trim() + (s.destination.trim().takeIf { it.isNotBlank() }?.let { ", $it" } ?: "") },
                confirmationNumber = s.confirmationNumber.trim().ifBlank { null },
                startEpochDay = parseUserDate(s.startDate),
                endEpochDay = parseUserDate(s.endDate),
                destination = s.destination.trim().ifBlank { null },
                loyaltyProgram = s.program,
                loyaltyNumberOnBooking = s.loyaltyNumberOnBooking,
                notes = s.notes.trim().ifBlank { null },
            )
            closeAdd()
        }
    }

    fun markLoyaltyAttached(trip: Trip) {
        viewModelScope.launch { tripRepository.markLoyaltyAttached(trip.id, trip.loyaltyProgram) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { tripRepository.delete(id) }
    }
}
