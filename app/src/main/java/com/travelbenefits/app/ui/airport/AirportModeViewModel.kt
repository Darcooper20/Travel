package com.travelbenefits.app.ui.airport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.TripRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.TravelPerk
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class LoungeAccess(val cardName: String, val perk: TravelPerk, val isPaymentCard: Boolean)

data class AirportTrip(
    val trip: Trip,
    val segments: List<TripSegment>,
    val loyalty: LoyaltyAccount?,
    val lounges: List<LoungeAccess>,
    val bagPerks: List<LoungeAccess>,
)

data class AirportUiState(val trips: List<AirportTrip> = emptyList(), val cachedAt: Long = System.currentTimeMillis())

/** Flights within the next 48 hours with everything needed at the airport, from local data only (works offline). */
@HiltViewModel
class AirportModeViewModel @Inject constructor(
    tripRepository: TripRepository,
    walletRepository: WalletRepository,
    loyaltyRepository: LoyaltyRepository,
) : ViewModel() {
    val uiState: StateFlow<AirportUiState> = combine(
        tripRepository.observeTrips(),
        tripRepository.observeAllSegments(),
        walletRepository.observeResolvedCards(),
        loyaltyRepository.observeAccounts(),
    ) { trips, segments, cards, accounts ->
        val today = LocalDate.now().toEpochDay()
        val soon = trips.filter { it.kind == TripKind.FLIGHT && it.isUpcoming(today) && (it.startEpochDay ?: Long.MAX_VALUE) - today <= 2 }
        AirportUiState(
            soon.map { t ->
                val catalogCards = cards.filterIsInstance<ResolvedWalletCard.Catalog>()
                AirportTrip(
                    trip = t,
                    segments = segments.filter { it.tripId == t.id },
                    loyalty = accounts.firstOrNull { it.program == t.loyaltyProgram },
                    lounges = catalogCards.flatMap { c -> c.entry.travelPerks.filter { it.kind == TravelPerk.Kind.LOUNGE }.map { LoungeAccess(c.displayName, it, c.walletCard.id == t.paymentCardId) } },
                    bagPerks = catalogCards.flatMap { c -> c.entry.travelPerks.filter { it.kind == TravelPerk.Kind.CHECKED_BAG || it.kind == TravelPerk.Kind.PRIORITY_BOARDING }.map { LoungeAccess(c.displayName, it, c.walletCard.id == t.paymentCardId) } },
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AirportUiState())
}
