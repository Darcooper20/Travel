package com.travelbenefits.app.domain

import com.travelbenefits.app.domain.model.TravelPerk

/**
 * Generic documentation checklists and typical deadlines per protection
 * kind. Deliberately generic: the card's benefits guide governs, and the
 * app says so. Nothing here asserts that a specific claim is covered.
 */
object ProtectionGuide {
    data class Guide(val kind: TravelPerk.Kind, val typicalDeadline: String, val documents: List<String>, val steps: List<String>)

    fun guide(kind: TravelPerk.Kind): Guide? = when (kind) {
        TravelPerk.Kind.TRIP_DELAY -> Guide(kind, "Notify the benefits administrator within 60 days of the delay (typical; check the guide)", listOf("Itinerary/ticket showing the fare charged to the card", "Airline delay/cancellation statement", "Receipts for meals, lodging, essentials", "Card statement showing the charge"), listOf("Call the number on the back of the card or the benefits guide", "Open a claim and upload documents", "Keep originals until paid"))
        TravelPerk.Kind.TRIP_CANCELLATION -> Guide(kind, "Notify within 20 days of the cancellation/interruption (typical); claim forms within 90 days", listOf("Booking confirmations and cancellation notices", "Proof of the covered reason (medical note, weather advisory...)", "Refund denials from the carrier/hotel", "Card statement showing the charge"), listOf("Request refunds from the provider first", "File with the benefits administrator with the denial", "Track the claim number"))
        TravelPerk.Kind.BAGGAGE_DELAY -> Guide(kind, "Report to the airline immediately; claim within 60 days (typical)", listOf("Airline property irregularity report", "Boarding passes", "Receipts for essentials bought during the delay"), listOf("File the airline report before leaving the airport", "Keep receipts", "Submit to the benefits administrator"))
        TravelPerk.Kind.RENTAL_CDW -> Guide(kind, "Report within 60 days of the incident (typical); claim documents within 100 days", listOf("Rental agreement showing the CDW was declined", "Police/incident report", "Rental company damage estimate and photos", "Card statement showing the full rental charged"), listOf("Decline the rental company's waiver at the counter", "Photograph the car before and after", "Notify the administrator promptly"))
        TravelPerk.Kind.TRAVEL_ACCIDENT -> Guide(kind, "Per the benefits guide", listOf("Ticket charged to the card", "Medical/official reports"), listOf("Contact the benefits administrator"))
        else -> null
    }
}
