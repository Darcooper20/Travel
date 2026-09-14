package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.BenefitItem
import com.travelbenefits.app.domain.model.BenefitKind
import com.travelbenefits.app.domain.model.Trip
import com.travelbenefits.app.domain.model.TripKind
import java.time.LocalDate
import javax.inject.Inject

/** Finds appropriate uses for certificates among planned trips, with the deadline and restriction caveats spelled out. */
class CertificateMatcher @Inject constructor() {

    data class Suggestion(val certificate: BenefitItem, val trip: Trip?, val fit: Fit, val reasons: List<String>)
    enum class Fit(val label: String) { GOOD("Good fit"), POSSIBLE("Possible - check restrictions"), NONE("No matching trip yet") }

    fun suggest(certificates: List<BenefitItem>, trips: List<Trip>, today: LocalDate = LocalDate.now()): List<Suggestion> =
        certificates.filter { !it.isUsed }.map { cert ->
            val candidates = trips.filter { t ->
                t.isUpcoming(today.toEpochDay()) && t.certificateId == null && when (cert.kind) {
                    BenefitKind.FREE_NIGHT -> t.kind == TripKind.HOTEL
                    BenefitKind.COMPANION, BenefitKind.UPGRADE -> t.kind == TripKind.FLIGHT
                    BenefitKind.LOUNGE_PASS -> t.kind == TripKind.FLIGHT
                    else -> true
                } && (cert.program == null || t.loyaltyProgram == cert.program)
            }.sortedBy { it.startEpochDay ?: Long.MAX_VALUE }
            val best = candidates.firstOrNull()
            val reasons = mutableListOf<String>()
            val expiry = cert.expiresEpochDay
            var fit = if (best == null) Fit.NONE else Fit.POSSIBLE
            if (best != null) {
                if (expiry != null && best.startEpochDay != null && best.startEpochDay > expiry) { reasons += "Trip starts after the certificate expires; most programs need the stay completed (not just booked) before expiry."; fit = Fit.NONE }
                if (cert.program != null && best.loyaltyProgram == cert.program) reasons += "Same program as the booking."
                if (cert.valueUsd != null && best.cashPriceUsd != null) reasons += if (best.cashPriceUsd > cert.valueUsd * 1.5) "Stay is well above the certificate's typical value - check the category/point cap and whether top-ups are allowed." else "Stay price is within the certificate's typical range."
                if (fit != Fit.NONE && reasons.none { it.startsWith("Stay is well") }) fit = Fit.GOOD
                reasons += "Verify guest/ownership rules: certificates are usually for the cardholder's own stay."
            }
            if (expiry != null) reasons += "Expires ${LocalDate.ofEpochDay(expiry)} (${expiry - today.toEpochDay()} days)."
            Suggestion(cert, best, fit, reasons)
        }.sortedBy { it.certificate.expiresEpochDay ?: Long.MAX_VALUE }
}
