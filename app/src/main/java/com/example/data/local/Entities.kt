package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracked_channels",
    indices = [Index(value = ["channelId"], unique = true)]
)
data class TrackedChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val channelId: String,
    val handle: String,
    val displayName: String,
    val niche: String,
    val healthStatus: String,
    val avgViewsPerVideo: Long,
    val totalRecentViews: Long,
    val avgStarRating: Double,
    val recentVideoCount: Int,
    val autoMonitor: Boolean = true,
    val lastSyncedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "ingested_videos")
data class IngestedVideoEntity(
    @PrimaryKey val videoId: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val publishedIso: String,
    val viewCount: Long = 0L,
    val starRating: Double = 0.0,
    val isQueuedToStudio: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "generated_clips")
data class GeneratedClipEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val sourceTitle: String,
    val sourceUrl: String,
    val localVideoUri: String? = null,
    val exportedMp4Path: String? = null,
    val thumbnailUrl: String? = null,
    val clipTitle: String,
    val seoDescription: String,
    val hashtags: String,
    val startSec: Int,
    val endSec: Int,
    val viralityScore: Int,
    val hookStrength: Int,
    val completeness: Int,
    val emotionalPayoff: Int,
    val framingMode: String,
    val subtitleStyle: String,
    val loudnessNormalized: Boolean = true,
    val silenceTrimmed: Boolean = true,
    val transcriptSnippet: String,
    val stickerText: String,
    val uploadStatus: String = "DRAFT",
    val scheduledLabel: String = "Unscheduled",
    val isSampleAiOutput: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "niche_playbooks")
data class NichePlaybookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val niche: String,
    val hookFormula: String,
    val hookVelocityWps: Double,
    val topTagsCsv: String,
    val dominantPalette: String,
    val recommendedCaptionStyle: String,
    val avgRetentionBoostPct: Int,
    val isSampleAiOutput: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
