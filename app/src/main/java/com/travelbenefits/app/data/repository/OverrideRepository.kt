package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.ActionStateDao
import com.travelbenefits.app.data.local.dao.UserOverrideDao
import com.travelbenefits.app.data.local.entity.ActionStateEntity
import com.travelbenefits.app.data.local.entity.UserOverrideEntity
import com.travelbenefits.app.domain.model.ActionState
import com.travelbenefits.app.domain.model.RewardCurrency
import com.travelbenefits.app.domain.model.TravelPerk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** User corrections and action-item states. These win over anything a sync computes. */
@Singleton
class OverrideRepository @Inject constructor(
    private val overrideDao: UserOverrideDao,
    private val actionStateDao: ActionStateDao,
) {
    // ---- Overrides ----
    fun observeOverrides(): Flow<Map<Pair<String, String>, String>> =
        overrideDao.observeAll().map { list -> list.associate { (it.scope to it.overrideKey) to it.value } }

    suspend fun get(scope: String, key: String): String? = overrideDao.find(scope, key)?.value

    suspend fun set(scope: String, key: String, value: String?) {
        if (value == null) overrideDao.delete(scope, key) else overrideDao.upsert(UserOverrideEntity(scope, key, value, System.currentTimeMillis()))
    }

    suspend fun getAll(): Map<Pair<String, String>, String> = overrideDao.getAll().associate { (it.scope to it.overrideKey) to it.value }

    /** Point valuations the user set, in cents per point, by currency. */
    fun valuations(overrides: Map<Pair<String, String>, String>): Map<RewardCurrency, Double> =
        RewardCurrency.entries.mapNotNull { c -> overrides["currency:${c.name}" to KEY_VALUATION]?.toDoubleOrNull()?.let { c to it } }.toMap()

    fun perkValues(overrides: Map<Pair<String, String>, String>, walletCardId: Long): Map<TravelPerk.Kind, Double> =
        TravelPerk.Kind.entries.mapNotNull { k -> overrides["card:$walletCardId" to "perk:${k.name}"]?.toDoubleOrNull()?.let { k to it } }.toMap()

    /** Manual "spent so far this cap period" values: card -> capKey -> USD. */
    fun capSpend(overrides: Map<Pair<String, String>, String>): Map<Long, Map<String, Double>> =
        overrides.entries.filter { it.key.first.startsWith("card:") && it.key.second.startsWith("cap:") }
            .groupBy({ it.key.first.removePrefix("card:").toLong() }, { it.key.second.removePrefix("cap:") to (it.value.toDoubleOrNull() ?: 0.0) })
            .mapValues { it.value.toMap() }

    // ---- Action states ----
    fun observeActionStates(): Flow<Map<String, Pair<ActionState, Long?>>> =
        actionStateDao.observeAll().map { list -> list.associate { it.actionKey to (it.state to it.untilEpochDay) } }

    suspend fun setActionState(key: String, state: ActionState, untilEpochDay: Long? = null) {
        val previous = actionStateDao.find(key)?.state
        actionStateDao.upsert(ActionStateEntity(key, state, untilEpochDay, previous, System.currentTimeMillis()))
    }

    /** Restores the previous state (or removes the record when there was none). */
    suspend fun undoActionState(key: String) {
        val current = actionStateDao.find(key) ?: return
        val prev = current.previousState
        if (prev == null || prev == ActionState.OPEN) actionStateDao.delete(key) else actionStateDao.upsert(current.copy(state = prev, previousState = null, updatedAt = System.currentTimeMillis()))
    }

    companion object {
        const val KEY_VALUATION = "valuationCents"
        const val KEY_AU_COUNT = "authorizedUsers"
        const val KEY_BONUS_POINTS = "bonusPoints"
        const val SCOPE_GLOBAL = "global"
    }
}
