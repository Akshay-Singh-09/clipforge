package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackedChannelDao {
    @Query("SELECT * FROM tracked_channels ORDER BY avgViewsPerVideo DESC, lastSyncedAt DESC")
    fun observeAll(): Flow<List<TrackedChannelEntity>>

    @Query("SELECT * FROM tracked_channels")
    suspend fun getAllOnce(): List<TrackedChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(channel: TrackedChannelEntity): Long

    @Query("UPDATE tracked_channels SET autoMonitor = :enabled WHERE id = :id")
    suspend fun setAutoMonitor(id: Long, enabled: Boolean)

    @Query(
        "UPDATE tracked_channels SET displayName = :displayName, healthStatus = :healthStatus, " +
            "avgViewsPerVideo = :avgViews, totalRecentViews = :totalViews, avgStarRating = :avgRating, " +
            "recentVideoCount = :videoCount, lastSyncedAt = :timestamp WHERE channelId = :channelId"
    )
    suspend fun updateLiveMetrics(
        channelId: String,
        displayName: String,
        healthStatus: String,
        avgViews: Long,
        totalViews: Long,
        avgRating: Double,
        videoCount: Int,
        timestamp: Long
    )

    @Query("DELETE FROM tracked_channels WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface IngestedVideoDao {
    @Query("SELECT * FROM ingested_videos ORDER BY publishedIso DESC, fetchedAt DESC")
    fun observeAll(): Flow<List<IngestedVideoEntity>>

    @Query("SELECT * FROM ingested_videos")
    suspend fun getAllOnce(): List<IngestedVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(videos: List<IngestedVideoEntity>)

    @Query("UPDATE ingested_videos SET isQueuedToStudio = :queued WHERE videoId = :videoId")
    suspend fun markQueued(videoId: String, queued: Boolean)

    @Query("DELETE FROM ingested_videos WHERE channelId = :channelId")
    suspend fun deleteForChannel(channelId: String)
}

@Dao
interface GeneratedClipDao {
    @Query("SELECT * FROM generated_clips ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<GeneratedClipEntity>>

    @Query("SELECT * FROM generated_clips")
    suspend fun getAllOnce(): List<GeneratedClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(clip: GeneratedClipEntity): Long

    @Update
    suspend fun update(clip: GeneratedClipEntity)

    @Query("UPDATE generated_clips SET uploadStatus = :status, scheduledLabel = :scheduledLabel WHERE id = :id")
    suspend fun updateUploadState(id: Long, status: String, scheduledLabel: String)

    @Query("DELETE FROM generated_clips WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface NichePlaybookDao {
    @Query("SELECT * FROM niche_playbooks ORDER BY updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<NichePlaybookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(playbook: NichePlaybookEntity): Long
}
