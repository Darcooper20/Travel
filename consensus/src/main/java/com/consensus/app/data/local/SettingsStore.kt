package com.consensus.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.consensus.app.domain.DefaultModels
import com.consensus.app.domain.ProviderId
import com.consensus.app.domain.Tier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "consensus_settings")

/** Non-secret settings: model IDs per provider/tier and the per-question defaults. */
data class AppSettings(
    val modelIds: Map<ProviderId, Map<Tier, String>>,
    val defaultTier: Map<ProviderId, Tier>,
    val enabled: Map<ProviderId, Boolean>,
    val defaultJudge: ProviderId,
    val defaultRounds: Int,
    val stopWhenAgreed: Boolean,
    val webSearch: Boolean,
) {
    fun modelId(p: ProviderId, t: Tier): String =
        modelIds[p]?.get(t)?.takeIf { it.isNotBlank() } ?: DefaultModels.forProvider(p, t)

    companion object {
        val DEFAULT = AppSettings(
            modelIds = ProviderId.values().associateWith { p -> Tier.values().associateWith { t -> DefaultModels.forProvider(p, t) } },
            defaultTier = ProviderId.values().associateWith { Tier.BALANCED },
            enabled = ProviderId.values().associateWith { true },
            defaultJudge = ProviderId.ANTHROPIC,
            defaultRounds = 1,
            stopWhenAgreed = true,
            webSearch = true,
        )
    }
}

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun save(s: AppSettings) {
        context.dataStore.edit { prefs ->
            ProviderId.values().forEach { p ->
                Tier.values().forEach { t -> prefs[modelKey(p, t)] = s.modelId(p, t) }
                prefs[tierKey(p)] = (s.defaultTier[p] ?: Tier.BALANCED).name
                prefs[enabledKey(p)] = s.enabled[p] ?: true
            }
            prefs[JUDGE] = s.defaultJudge.name
            prefs[ROUNDS] = s.defaultRounds
            prefs[STOP_WHEN_AGREED] = s.stopWhenAgreed
            prefs[WEB_SEARCH] = s.webSearch
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings.DEFAULT
        return AppSettings(
            modelIds = ProviderId.values().associateWith { p ->
                Tier.values().associateWith { t -> this[modelKey(p, t)] ?: DefaultModels.forProvider(p, t) }
            },
            defaultTier = ProviderId.values().associateWith { p ->
                this[tierKey(p)]?.let { runCatching { Tier.valueOf(it) }.getOrNull() } ?: Tier.BALANCED
            },
            enabled = ProviderId.values().associateWith { p -> this[enabledKey(p)] ?: true },
            defaultJudge = this[JUDGE]?.let { runCatching { ProviderId.valueOf(it) }.getOrNull() } ?: d.defaultJudge,
            defaultRounds = this[ROUNDS] ?: d.defaultRounds,
            stopWhenAgreed = this[STOP_WHEN_AGREED] ?: d.stopWhenAgreed,
            webSearch = this[WEB_SEARCH] ?: d.webSearch,
        )
    }

    private fun modelKey(p: ProviderId, t: Tier) = stringPreferencesKey("model_${p.name}_${t.name}")
    private fun tierKey(p: ProviderId) = stringPreferencesKey("tier_${p.name}")
    private fun enabledKey(p: ProviderId) = booleanPreferencesKey("enabled_${p.name}")

    private companion object {
        val JUDGE = stringPreferencesKey("default_judge")
        val ROUNDS = intPreferencesKey("default_rounds")
        val STOP_WHEN_AGREED = booleanPreferencesKey("stop_when_agreed")
        val WEB_SEARCH = booleanPreferencesKey("web_search")
    }
}
