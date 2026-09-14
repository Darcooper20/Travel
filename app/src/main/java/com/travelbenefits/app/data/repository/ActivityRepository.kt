package com.travelbenefits.app.data.repository

import com.travelbenefits.app.data.local.dao.ActivityEventDao
import com.travelbenefits.app.data.local.toDomain
import com.travelbenefits.app.domain.model.ActivityEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityRepository @Inject constructor(
    private val dao: ActivityEventDao,
) {
    fun observeRecent(limit: Int = 100): Flow<List<ActivityEvent>> = dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun markAllRead() = dao.markAllRead()

    suspend fun clear() = dao.clear()
}
