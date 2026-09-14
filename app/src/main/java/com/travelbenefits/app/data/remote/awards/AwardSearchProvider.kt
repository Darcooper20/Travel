package com.travelbenefits.app.data.remote.awards

import com.travelbenefits.app.domain.model.AwardSearchRequest
import com.travelbenefits.app.domain.model.AwardSearchResponse

/**
 * Award-availability source. Implementations must label results with their
 * true nature; "no results" from any provider never proves there is no
 * availability.
 */
interface AwardSearchProvider {
    val id: String
    val displayName: String
    /** Human description of what the provider actually covers. */
    val coverage: String
    suspend fun isAvailable(): Boolean
    suspend fun search(request: AwardSearchRequest): Result<AwardSearchResponse>
}
