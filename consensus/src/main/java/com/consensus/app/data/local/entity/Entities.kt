package com.consensus.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "exchanges",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("threadId")],
)
data class ExchangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val question: String,
    val finalAnswer: String? = null,
    /** RUNNING, DONE or ERROR. */
    val status: String,
    val error: String? = null,
    /** Serialized [com.consensus.app.domain.RunTranscript]. */
    val transcriptJson: String? = null,
    val createdAt: Long,
) {
    companion object {
        const val RUNNING = "RUNNING"
        const val DONE = "DONE"
        const val ERROR = "ERROR"
    }
}
