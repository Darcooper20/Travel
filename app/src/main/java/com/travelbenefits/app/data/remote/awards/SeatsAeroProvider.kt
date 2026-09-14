package com.travelbenefits.app.data.remote.awards

import com.travelbenefits.app.data.local.SecurePrefs
import com.travelbenefits.app.domain.model.AwardResult
import com.travelbenefits.app.domain.model.AwardSearchRequest
import com.travelbenefits.app.domain.model.AwardSearchResponse
import com.travelbenefits.app.domain.model.Cabin
import com.travelbenefits.app.domain.model.ResultKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * seats.aero Partner API adapter. seats.aero serves *cached* award
 * availability it has scraped from airline programs, so every result is
 * labelled CACHED_INVENTORY with the provider's own updated-at stamp.
 *
 * Requires a Partner API key (seats.aero Pro subscription, paid) entered in
 * Settings. Without it the provider reports itself unavailable. Field
 * names follow the public partner API documentation; parsing is lenient
 * so a schema drift degrades to "unknown" fields rather than failure.
 */
@Singleton
class SeatsAeroProvider @Inject constructor(
    private val client: OkHttpClient,
    private val securePrefs: SecurePrefs,
    private val json: Json,
) : AwardSearchProvider {
    override val id = "seats_aero"
    override val displayName = "seats.aero (cached availability)"
    override val coverage = "Cached award availability across the programs seats.aero indexes (United, American, Delta, Alaska, Aeroplan, Virgin Atlantic, and others). Not real-time; not every program; hotel awards not covered."

    override suspend fun isAvailable(): Boolean = !securePrefs.seatsAeroApiKey.isNullOrBlank()

    override suspend fun search(request: AwardSearchRequest): Result<AwardSearchResponse> = withContext(Dispatchers.IO) {
        val key = securePrefs.seatsAeroApiKey
        if (key.isNullOrBlank()) return@withContext Result.failure(IllegalStateException("Add a seats.aero Partner API key in Settings (Pro subscription required)."))
        runCatching {
            val from = runCatching { LocalDate.parse(request.dateFrom).minusDays(request.flexibleDays.toLong()) }.getOrElse { LocalDate.now() }
            val to = runCatching { LocalDate.parse(request.dateTo).plusDays(request.flexibleDays.toLong()) }.getOrElse { from.plusDays(7) }
            val origins = (listOf(request.origin) + request.nearbyOrigins).map { it.trim().uppercase() }.filter { it.isNotBlank() }.joinToString(",")
            val destinations = (listOf(request.destination) + request.nearbyDestinations).map { it.trim().uppercase() }.filter { it.isNotBlank() }.joinToString(",")
            val url = "https://seats.aero/partnerapi/search?origin_airport=$origins&destination_airport=$destinations&cabin=${request.cabin.seatsAeroCode}&start_date=$from&end_date=$to&take=200"
            val response = client.newCall(Request.Builder().url(url).header("Partner-Authorization", key).header("accept", "application/json").build()).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("seats.aero returned HTTP ${response.code}${if (response.code == 401 || response.code == 403) " - check the API key" else ""}")
            val root = json.parseToJsonElement(body).jsonObject
            val rows = (root["data"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            val prefix = when (request.cabin) { Cabin.ECONOMY -> "Y"; Cabin.PREMIUM -> "W"; Cabin.BUSINESS -> "J"; Cabin.FIRST -> "F" }
            val results = rows.mapNotNull { row ->
                val available = row["${prefix}Available"]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
                if (!available) return@mapNotNull null
                val route = row["Route"] as? JsonObject
                val origin = route?.get("OriginAirport")?.jsonPrimitive?.contentOrNull ?: request.origin
                val dest = route?.get("DestinationAirport")?.jsonPrimitive?.contentOrNull ?: request.destination
                val source = route?.get("Source")?.jsonPrimitive?.contentOrNull ?: row["Source"]?.jsonPrimitive?.contentOrNull
                val seats = row["${prefix}RemainingSeats"]?.jsonPrimitive?.intOrNull
                val direct = row["${prefix}Direct"]?.jsonPrimitive?.booleanOrNull
                val cost = row["${prefix}MileageCost"]?.jsonPrimitive?.contentOrNull?.replace(",", "")?.toLongOrNull()
                val taxes = row["${prefix}TotalTaxes"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.let { it / 100.0 }
                AwardResult(
                    kind = ResultKind.CACHED_INVENTORY, provider = displayName, program = source, origin = origin, destination = dest,
                    date = row["Date"]?.jsonPrimitive?.contentOrNull ?: "", cabin = request.cabin, pointsPerPassenger = cost, taxesFeesUsd = taxes,
                    seatsRemaining = seats, isDirect = direct, mixedCabin = null, airlines = row["${prefix}Airlines"]?.jsonPrimitive?.contentOrNull,
                    bookingUrl = null, asOf = row["UpdatedAt"]?.jsonPrimitive?.contentOrNull, note = if (seats != null && seats < request.passengers) "Fewer seats than passengers" else null,
                )
            }.filter { request.maxConnections == null || request.maxConnections > 0 || it.isDirect == true }
            AwardSearchResponse(
                request, results, displayName, coverage,
                listOfNotNull(
                    "Cached data: verify on the program's site before transferring points.",
                    if (results.isEmpty()) "No cached availability shown - this does not prove there is none (coverage gaps, snapshot age)." else null,
                    if (request.passengers > 1) "Seat counts are per date/cabin as last seen; ${request.passengers} passengers need that many remaining seats." else null,
                ),
                System.currentTimeMillis(),
            )
        }
    }
}
