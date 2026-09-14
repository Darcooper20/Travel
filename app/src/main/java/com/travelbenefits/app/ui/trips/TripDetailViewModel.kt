package com.travelbenefits.app.ui.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.travelbenefits.app.data.repository.BenefitsRepository
import com.travelbenefits.app.data.repository.LoyaltyRepository
import com.travelbenefits.app.data.repository.OverrideRepository
import com.travelbenefits.app.data.repository.TripRepository
import com.travelbenefits.app.data.repository.WalletRepository
import com.travelbenefits.app.data.catalog.LoyaltyProgramCatalog
import com.travelbenefits.app.domain.TripPaymentComparator
import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BookingChannel
import com.travelbenefits.app.domain.model.ExpectationKind
import com.travelbenefits.app.domain.model.LoyaltyAccount
import com.travelbenefits.app.domain.model.LoyaltyNumberState
import com.travelbenefits.app.domain.model.LoyaltyProgram
import com.travelbenefits.app.domain.model.ResolvedWalletCard
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripEvent
import com.travelbenefits.app.domain.model.TripEventKind
import com.travelbenefits.app.domain.model.TripKind
import com.travelbenefits.app.domain.model.TripSegment
import com.travelbenefits.app.domain.model.TripStatus
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

data class PaymentForm(
    val cashPrice: String = "",
    val pointsRequired: String = "",
    val awardFees: String = "0",
    val channel: BookingChannel = BookingChannel.DIRECT,
    val paymentCardId: Long? = null,
    val certificateId: Long? = null,
    val cancellationTerms: String = "",
    val travelers: String = "",
    val departureTime: String = "",
    val timeZone: String = "",
    val comparison: TripPaymentComparator.Comparison? = null,
)

data class TripDetailUiState(
    val trip: Trip? = null,
    val segments: List<TripSegment> = emptyList(),
    val events: List<TripEvent> = emptyList(),
    val cards: List<ResolvedWalletCard> = emptyList(),
    val accounts: List<LoyaltyAccount> = emptyList(),
    val certificates: List<BenefitItem> = emptyList(),
    val form: PaymentForm = PaymentForm(),
    val message: String? = null,
)

@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    walletRepository: WalletRepository,
    loyaltyRepository: LoyaltyRepository,
    benefitsRepository: BenefitsRepository,
    private val overrideRepository: OverrideRepository,
    private val comparator: TripPaymentComparator,
) : ViewModel() {

    private val tripId: Long = checkNotNull(savedStateHandle.get<String>("tripId")).toLong()
    private val form = MutableStateFlow(PaymentForm())
    private val message = MutableStateFlow<String?>(null)
    private var seeded = false

    val uiState: StateFlow<TripDetailUiState> = combine(
        combine(tripRepository.observeTrip(tripId), tripRepository.observeSegments(tripId), tripRepository.observeEvents(tripId)) { t, s, e -> Triple(t, s, e) },
        combine(walletRepository.observeResolvedCards(), loyaltyRepository.observeAccounts(), benefitsRepository.observeItems()) { c, a, b -> Triple(c, a, b) },
        combine(form, overrideRepository.observeOverrides(), message) { f, o, m -> Triple(f, o, m) },
    ) { (trip, segments, events), (cards, accounts, certs), (f, overrides, msg) ->
        if (trip != null && !seeded) {
            seeded = true
            form.value = f.copy(
                cashPrice = trip.cashPriceUsd?.let { "%.2f".format(it) } ?: f.cashPrice,
                pointsRequired = trip.pointsUsed?.toString() ?: f.pointsRequired,
                channel = trip.bookingChannel ?: f.channel,
                paymentCardId = trip.paymentCardId,
                certificateId = trip.certificateId,
                cancellationTerms = trip.cancellationTerms.orEmpty(),
                travelers = trip.travelers.orEmpty(),
                departureTime = trip.departureTimeLocal.orEmpty(),
                timeZone = trip.timeZoneId.orEmpty(),
            )
        }
        val comparison = trip?.let {
            comparator.compare(
                it, f.cashPrice.replace("$", "").replace(",", "").toDoubleOrNull(), f.pointsRequired.replace(",", "").toLongOrNull(), f.awardFees.toDoubleOrNull() ?: 0.0,
                cards, accounts, certs, overrideRepository.valuations(overrides), f.channel,
            )
        }
        TripDetailUiState(trip, segments, events, cards, accounts, certs.filter { !it.isUsed }, f.copy(comparison = comparison), msg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TripDetailUiState())

    fun updateForm(transform: (PaymentForm) -> PaymentForm) { form.value = transform(form.value) }

    fun setLoyaltyState(state: LoyaltyNumberState, program: LoyaltyProgram?) {
        viewModelScope.launch { tripRepository.setLoyaltyNumberState(tripId, state, program); tripRepository.addEvent(tripId, TripEventKind.NOTE, "Loyalty number marked ${state.label.lowercase()}") }
    }

    fun setStatus(status: TripStatus) {
        viewModelScope.launch { tripRepository.setStatus(tripId, status, "Marked ${status.label.lowercase()} by you") }
    }

    /** Saves payment/booking details and creates the expectations that reconciliation will check later. */
    fun recordBooking() {
        val f = form.value
        val state = uiState.value
        val trip = state.trip ?: return
        viewModelScope.launch {
            val cash = f.cashPrice.replace("$", "").replace(",", "").toDoubleOrNull()
            val points = f.pointsRequired.replace(",", "").toLongOrNull()
            tripRepository.recordPayment(tripId, f.paymentCardId, if (points != null && points > 0) trip.loyaltyProgram else null, points, cash, f.certificateId, f.channel, f.cancellationTerms.ifBlank { null }, f.travelers.ifBlank { null })
            tripRepository.setDepartureTime(tripId, f.departureTime.ifBlank { null }, f.timeZone.ifBlank { null })
            val dueBy = (trip.endEpochDay ?: trip.startEpochDay ?: LocalDate.now().toEpochDay()) + 45
            val cardOption = f.comparison?.cashOptions?.firstOrNull { it.card.walletCard.id == f.paymentCardId }
            if (cardOption != null && cash != null && (points == null || points == 0L)) {
                tripRepository.ensureExpectation(ExpectationKind.CARD_REWARDS, "TRIP", tripId.toString(), f.paymentCardId, null, cardOption.rewardsEarned, if (cardOption.rewardCurrency.displayAsPercent) "USD" else "points", dueBy, "${cardOption.effectiveRateLabel} on ${trip.title}")
            }
            val program = trip.loyaltyProgram
            if (program != null && trip.loyaltyNumberState == LoyaltyNumberState.CONFIRMED && trip.kind == TripKind.HOTEL) {
                val profile = LoyaltyProgramCatalog.profileFor(program)
                val isAward = points != null && points > 0
                if (!isAward && cash != null && profile.basePointsPerDollar != null) {
                    val tier = state.accounts.firstOrNull { it.program == program }?.tier
                    val expected = cash * profile.basePointsPerDollar * (1 + profile.eliteBonusFor(tier))
                    tripRepository.ensureExpectation(ExpectationKind.PROGRAM_POINTS, "TRIP", tripId.toString(), null, program, expected, "points", dueBy, "Base earn on ${trip.title} (taxes/fees usually excluded)")
                }
                val nights = trip.nights
                if (nights != null && (!isAward || profile.awardStaysCountForStatus == true)) {
                    tripRepository.ensureExpectation(ExpectationKind.QUALIFYING_ACTIVITY, "TRIP", tripId.toString(), null, program, nights.toDouble(), "nights", dueBy, "Elite nights for ${trip.title}")
                }
            }
            f.certificateId?.let { tripRepository.addEvent(tripId, TripEventKind.BENEFIT, "Certificate #$it applied") }
            message.value = "Booking recorded; expectations created for reconciliation."
        }
    }

    fun clearMessage() { message.value = null }
}
