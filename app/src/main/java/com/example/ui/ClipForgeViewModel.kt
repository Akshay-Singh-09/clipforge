package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ClipForgeRepository
import com.example.data.ai.PlaceholderAiRepository
import com.example.data.local.ClipForgeDatabase
import com.example.data.local.GeneratedClipEntity
import com.example.data.local.IngestedVideoEntity
import com.example.data.local.NichePlaybookEntity
import com.example.data.local.ThemePreferencesRepository
import com.example.data.local.TrackedChannelEntity
import com.example.data.media.LocalVideoInspector
import com.example.data.media.LocalVideoTrimmer
import com.example.data.media.SubtitleExporter
import com.example.data.network.YouTubeFeedClient
import com.example.model.AsyncStatus
import com.example.model.ChannelHealthStatus
import com.example.model.DashboardSummary
import com.example.model.FramingMode
import com.example.model.LocalVideoMeta
import com.example.model.NicheLiveAnalytics
import com.example.model.SubtitleStyle
import com.example.model.ThemeMode
import com.example.model.UploadStatus
import com.example.model.VideoOEmbedInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class StudioConfigState(
    val sourceUrl: String = "",
    val sourceTitle: String = "",
    val sourceThumbnailUrl: String? = null,
    val localVideoMeta: LocalVideoMeta? = null,
    val localPreviewFrames: List<Bitmap> = emptyList(),
    val clipTitleInput: String = "",
    val captionScriptInput: String = "",
    val stickerTextInput: String = "",
    val startSec: Int = 0,
    val endSec: Int = 30,
    val maxDurationSec: Int = 180,
    val framingMode: FramingMode = FramingMode.SMART_CENTER,
    val subtitleStyle: SubtitleStyle = SubtitleStyle.HORMOZI,
    val loudnessNormalized: Boolean = true,
    val silenceTrimmed: Boolean = true,
    val oembedStatus: AsyncStatus<VideoOEmbedInfo> = AsyncStatus.Idle,
    val exportStatus: AsyncStatus<GeneratedClipEntity> = AsyncStatus.Idle,
    val selectedClipId: Long? = null
)

class ClipForgeViewModel(
    private val repository: ClipForgeRepository,
    private val themePreferences: ThemePreferencesRepository,
    private val localVideoInspector: LocalVideoInspector,
    private val localVideoTrimmer: LocalVideoTrimmer? = null
) : ViewModel() {

    val isFirebaseAiConnected: Boolean
        get() = repository.isFirebaseAiConnected

    val themeMode: StateFlow<ThemeMode> = themePreferences.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ThemeMode.SYSTEM
        )

    val trackedChannels: StateFlow<List<TrackedChannelEntity>> = repository.trackedChannels
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val ingestedVideos: StateFlow<List<IngestedVideoEntity>> = repository.ingestedVideos
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val generatedClips: StateFlow<List<GeneratedClipEntity>> = repository.generatedClips
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val nichePlaybooks: StateFlow<List<NichePlaybookEntity>> = repository.nichePlaybooks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val liveNicheAnalytics: StateFlow<List<NicheLiveAnalytics>> = combine(
        trackedChannels,
        ingestedVideos
    ) { channels, videos ->
        ClipForgeRepository.computeLiveNicheAnalytics(channels, videos)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        trackedChannels,
        ingestedVideos,
        generatedClips
    ) { channels, videos, clips ->
        val totalViews = videos.sumOf { it.viewCount }
        val rated = videos.filter { it.starRating > 0.0 }
        val avgRating = if (rated.isNotEmpty()) rated.map { it.starRating }.average() else 0.0
        val doingGreat = channels.count { it.healthStatus == ChannelHealthStatus.DOING_GREAT.name }
        val stable = channels.count { it.healthStatus == ChannelHealthStatus.STABLE.name }
        val attention = channels.count { it.healthStatus == ChannelHealthStatus.NEEDS_ATTENTION.name }
        val exported = clips.count { !it.exportedMp4Path.isNullOrBlank() || it.uploadStatus == UploadStatus.PUBLISHED.name }
        val series = videos.take(7).reversed().map { it.viewCount.toFloat().coerceAtLeast(1f) }
        DashboardSummary(
            totalViews = totalViews,
            trackedChannelCount = channels.size,
            ingestedVideoCount = videos.size,
            exportedClipCount = exported,
            avgRating = avgRating,
            viewSeries = series,
            doingGreatCount = doingGreat,
            stableCount = stable,
            needsAttentionCount = attention
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = DashboardSummary(0L, 0, 0, 0, 0.0, emptyList(), 0, 0, 0)
    )

    private val _studioState = MutableStateFlow(StudioConfigState())
    val studioState: StateFlow<StudioConfigState> = _studioState.asStateFlow()

    private val _rssSyncStatus = MutableStateFlow<AsyncStatus<Int>>(AsyncStatus.Idle)
    val rssSyncStatus: StateFlow<AsyncStatus<Int>> = _rssSyncStatus.asStateFlow()

    private val _nicheMiningStatus = MutableStateFlow<AsyncStatus<NichePlaybookEntity>>(AsyncStatus.Idle)
    val nicheMiningStatus: StateFlow<AsyncStatus<NichePlaybookEntity>> = _nicheMiningStatus.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun cycleThemeMode(currentlyDark: Boolean) {
        viewModelScope.launch {
            themePreferences.setThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.setThemeMode(mode)
        }
    }

    // --- Tracker Actions ---

    fun addChannel(channelInput: String, displayName: String, niche: String) {
        viewModelScope.launch {
            _rssSyncStatus.value = AsyncStatus.Loading
            val result = repository.addAndSyncChannel(channelInput, displayName, niche)
            result.onSuccess { added ->
                _rssSyncStatus.value = AsyncStatus.Success(added.recentVideoCount)
                _snackbarMessage.value = "Synced ${added.displayName} (${added.recentVideoCount} videos)"
            }.onFailure { err ->
                val msg = err.message ?: "Could not resolve or sync channel"
                _rssSyncStatus.value = AsyncStatus.Error(msg)
                _snackbarMessage.value = msg
            }
        }
    }

    fun toggleChannelMonitor(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.toggleChannelMonitor(id, enabled)
        }
    }

    fun deleteChannel(channel: TrackedChannelEntity) {
        viewModelScope.launch {
            repository.deleteChannel(channel)
            _snackbarMessage.value = "Removed ${channel.displayName}"
        }
    }

    fun syncSingleChannel(channelId: String) {
        viewModelScope.launch {
            _rssSyncStatus.value = AsyncStatus.Loading
            val res = repository.syncChannelRss(channelId)
            res.onSuccess { items ->
                _rssSyncStatus.value = AsyncStatus.Success(items.size)
                _snackbarMessage.value = "Synced ${items.size} videos via live RSS"
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "RSS sync failed"
                _rssSyncStatus.value = AsyncStatus.Error(msg)
                _snackbarMessage.value = "RSS sync failed: $msg"
            }
        }
    }

    fun syncAllChannels() {
        viewModelScope.launch {
            _rssSyncStatus.value = AsyncStatus.Loading
            val res = repository.syncAllTrackedChannels()
            res.onSuccess { count ->
                _rssSyncStatus.value = AsyncStatus.Success(count)
                _snackbarMessage.value = "Synced $count live videos"
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Network unavailable"
                _rssSyncStatus.value = AsyncStatus.Error(msg)
                _snackbarMessage.value = msg
            }
        }
    }

    fun sendRssVideoToStudio(video: IngestedVideoEntity) {
        viewModelScope.launch {
            repository.markVideoQueued(video.videoId)
            _studioState.update {
                it.copy(
                    sourceUrl = video.videoUrl,
                    sourceTitle = video.title,
                    sourceThumbnailUrl = video.thumbnailUrl,
                    clipTitleInput = video.title,
                    captionScriptInput = video.title,
                    stickerTextInput = video.channelName.uppercase().take(16),
                    localVideoMeta = null,
                    localPreviewFrames = emptyList(),
                    startSec = 0,
                    endSec = 30,
                    maxDurationSec = 180,
                    oembedStatus = AsyncStatus.Idle
                )
            }
            _snackbarMessage.value = "Loaded \"${video.title.take(26)}...\" into Studio"
        }
    }

    // --- Studio Actions ---

    fun updateStudioSourceUrl(url: String) {
        _studioState.update { it.copy(sourceUrl = url) }
    }

    fun updateClipTitleInput(title: String) {
        _studioState.update { it.copy(clipTitleInput = title) }
    }

    fun updateCaptionScriptInput(script: String) {
        _studioState.update { it.copy(captionScriptInput = script) }
    }

    fun updateStickerTextInput(sticker: String) {
        _studioState.update { it.copy(stickerTextInput = sticker) }
    }

    fun updateTrimRange(startSec: Int, endSec: Int) {
        _studioState.update {
            val maxSec = it.maxDurationSec.coerceAtLeast(10)
            val safeStart = startSec.coerceIn(0, (maxSec - 5).coerceAtLeast(0))
            val safeEnd = endSec.coerceIn((safeStart + 5).coerceAtMost(maxSec), maxSec)
            it.copy(
                startSec = safeStart,
                endSec = safeEnd
            )
        }
    }

    fun updateFramingMode(mode: FramingMode) {
        _studioState.update { it.copy(framingMode = mode) }
    }

    fun updateSubtitleStyle(style: SubtitleStyle) {
        _studioState.update { it.copy(subtitleStyle = style) }
    }

    fun updateLoudnessNormalized(enabled: Boolean) {
        _studioState.update { it.copy(loudnessNormalized = enabled) }
    }

    fun updateSilenceTrimmed(enabled: Boolean) {
        _studioState.update { it.copy(silenceTrimmed = enabled) }
    }

    fun selectClipForPreview(clipId: Long) {
        _studioState.update { it.copy(selectedClipId = clipId) }
    }

    fun fetchVideoMetadataFromUrl() {
        val rawUrl = _studioState.value.sourceUrl.trim()
        if (rawUrl.isBlank()) {
            _snackbarMessage.value = "Paste a YouTube video link first"
            return
        }
        viewModelScope.launch {
            _studioState.update { it.copy(oembedStatus = AsyncStatus.Loading) }
            val result = repository.fetchYouTubeOEmbed(rawUrl)
            result.onSuccess { info ->
                _studioState.update {
                    it.copy(
                        sourceUrl = info.videoUrl,
                        sourceTitle = info.title,
                        sourceThumbnailUrl = info.thumbnailUrl,
                        clipTitleInput = info.title,
                        captionScriptInput = info.title,
                        stickerTextInput = info.authorName.uppercase().take(16),
                        localVideoMeta = null,
                        localPreviewFrames = emptyList(),
                        oembedStatus = AsyncStatus.Success(info)
                    )
                }
                _snackbarMessage.value = "Fetched metadata for ${info.authorName}"
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Failed to inspect YouTube link"
                _studioState.update { it.copy(oembedStatus = AsyncStatus.Error(msg)) }
                _snackbarMessage.value = msg
            }
        }
    }

    fun loadLocalPickedVideo(uri: Uri) {
        viewModelScope.launch {
            val result = localVideoInspector.inspect(uri)
            result.onSuccess { inspected ->
                val totalSec = (inspected.meta.durationMs / 1000L).toInt().coerceAtLeast(10)
                val cleanTitle = inspected.meta.displayName.substringBeforeLast('.')
                _studioState.update {
                    it.copy(
                        localVideoMeta = inspected.meta,
                        localPreviewFrames = inspected.previewFrames,
                        sourceTitle = cleanTitle,
                        sourceUrl = "",
                        sourceThumbnailUrl = null,
                        clipTitleInput = cleanTitle,
                        captionScriptInput = cleanTitle,
                        stickerTextInput = "SHORT CUT",
                        startSec = 0,
                        endSec = totalSec.coerceAtMost(30),
                        maxDurationSec = totalSec,
                        oembedStatus = AsyncStatus.Idle
                    )
                }
                _snackbarMessage.value = "Loaded MP4 (${inspected.meta.width}x${inspected.meta.height}, ${totalSec}s)"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not read selected video"
            }
        }
    }

    fun createAndTrimClip() {
        val state = _studioState.value
        if (state.sourceTitle.isBlank() && state.localVideoMeta == null && state.sourceUrl.isBlank()) {
            _snackbarMessage.value = "Fetch a YouTube link or pick a local MP4 video first"
            return
        }
        viewModelScope.launch {
            _studioState.update { it.copy(exportStatus = AsyncStatus.Loading) }
            val result = repository.createAndTrimClip(
                sourceTitle = state.sourceTitle.ifBlank { "Studio Clip" },
                sourceUrl = state.sourceUrl,
                localVideoUri = state.localVideoMeta?.uriString,
                thumbnailUrl = state.sourceThumbnailUrl,
                clipTitle = state.clipTitleInput,
                captionScript = state.captionScriptInput,
                stickerText = state.stickerTextInput,
                startSec = state.startSec,
                endSec = state.endSec,
                framingMode = state.framingMode,
                subtitleStyle = state.subtitleStyle,
                loudnessNormalized = state.loudnessNormalized,
                silenceTrimmed = state.silenceTrimmed
            )
            result.onSuccess { saved ->
                _studioState.update {
                    it.copy(
                        exportStatus = AsyncStatus.Success(saved),
                        selectedClipId = saved.id
                    )
                }
                val actionMsg = if (!saved.exportedMp4Path.isNullOrBlank()) {
                    "Exported 9:16 MP4 clip (${saved.endSec - saved.startSec}s) & ready to play!"
                } else {
                    "Saved clip project!"
                }
                _snackbarMessage.value = actionMsg
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Clip creation failed"
                _studioState.update { it.copy(exportStatus = AsyncStatus.Error(msg)) }
                _snackbarMessage.value = msg
            }
        }
    }

    fun reExportClipMp4(clip: GeneratedClipEntity) {
        viewModelScope.launch {
            _studioState.update { it.copy(exportStatus = AsyncStatus.Loading) }
            val result = repository.reExportClipMp4(clip)
            result.onSuccess { updated ->
                _studioState.update {
                    it.copy(
                        exportStatus = AsyncStatus.Success(updated),
                        selectedClipId = updated.id
                    )
                }
                _snackbarMessage.value = "Rendered updated MP4 (${updated.endSec - updated.startSec}s)"
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Failed to render MP4"
                _studioState.update { it.copy(exportStatus = AsyncStatus.Error(msg)) }
                _snackbarMessage.value = msg
            }
        }
    }

    fun attachLocalVideoToExistingClip(clip: GeneratedClipEntity, uri: Uri) {
        viewModelScope.launch {
            _studioState.update { it.copy(exportStatus = AsyncStatus.Loading) }
            val inspected = localVideoInspector.inspect(uri).getOrNull()
            val totalSec = inspected?.let { (it.meta.durationMs / 1000L).toInt().coerceAtLeast(10) }
            val adjustedClip = if (totalSec != null) {
                val safeStart = clip.startSec.coerceIn(0, (totalSec - 5).coerceAtLeast(0))
                val safeEnd = clip.endSec.coerceIn((safeStart + 5).coerceAtMost(totalSec), totalSec)
                clip.copy(startSec = safeStart, endSec = safeEnd)
            } else {
                clip
            }
            val result = repository.attachLocalVideoToClip(adjustedClip, uri)
            result.onSuccess { updated ->
                _studioState.update {
                    it.copy(
                        exportStatus = AsyncStatus.Success(updated),
                        selectedClipId = updated.id,
                        localPreviewFrames = inspected?.previewFrames ?: it.localPreviewFrames
                    )
                }
                _snackbarMessage.value = "Trimmed attached MP4 (${updated.endSec - updated.startSec}s) & ready to play!"
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Could not trim selected MP4"
                _studioState.update { it.copy(exportStatus = AsyncStatus.Error(msg)) }
                _snackbarMessage.value = msg
            }
        }
    }

    fun saveClipMp4ToGallery(clip: GeneratedClipEntity) {
        val path = clip.exportedMp4Path
        val trimmer = localVideoTrimmer
        if (path.isNullOrBlank() || trimmer == null) {
            _snackbarMessage.value = "Render or trim the MP4 first"
            return
        }
        viewModelScope.launch {
            val res = trimmer.saveMp4ToMovies(File(path), clip.clipTitle)
            res.onSuccess {
                repository.updateClipUploadStatus(clip.id, UploadStatus.EXPORTED, "Saved to Movies/ClipForge")
                _snackbarMessage.value = "Saved MP4 to Movies/ClipForge!"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Could not save MP4 to Movies"
            }
        }
    }

    fun copyClipMp4ToUri(clip: GeneratedClipEntity, destUri: Uri) {
        val path = clip.exportedMp4Path
        val trimmer = localVideoTrimmer
        if (path.isNullOrBlank() || trimmer == null) {
            _snackbarMessage.value = "Render or trim the MP4 first"
            return
        }
        viewModelScope.launch {
            val res = trimmer.copyFileToUri(File(path), destUri)
            res.onSuccess {
                repository.updateClipUploadStatus(clip.id, UploadStatus.EXPORTED, "Exported to Device")
                _snackbarMessage.value = "Exported MP4 file successfully!"
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Failed to export MP4 file"
            }
        }
    }

    fun saveClipEdits(clip: GeneratedClipEntity) {
        viewModelScope.launch {
            try {
                repository.updateClip(clip)
                _snackbarMessage.value = "Saved clip adjustments"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Failed to save clip"
            }
        }
    }

    fun runSampleAiHookScore(clip: GeneratedClipEntity) {
        viewModelScope.launch {
            val res = repository.runSampleAiHookScore(clip)
            res.onSuccess { result ->
                _snackbarMessage.value = result.noticeMessage
            }.onFailure { err ->
                _snackbarMessage.value = err.localizedMessage ?: "Hook scoring failed"
            }
        }
    }

    fun queueClipForUpload(clipId: Long, status: UploadStatus, scheduledLabel: String) {
        viewModelScope.launch {
            repository.updateClipUploadStatus(clipId, status, scheduledLabel)
            _snackbarMessage.value = "Updated status: ${status.label}"
        }
    }

    fun deleteClip(clipId: Long) {
        viewModelScope.launch {
            repository.deleteClip(clipId)
            _snackbarMessage.value = "Removed clip"
        }
    }

    fun getShareableMp4Uri(path: String): Uri? {
        val file = File(path)
        if (!file.exists() || localVideoTrimmer == null) return null
        return localVideoTrimmer.getShareableUri(file)
    }

    suspend fun exportSubtitleFileUri(clip: GeneratedClipEntity, isAss: Boolean): Uri? {
        val trimmer = localVideoTrimmer ?: return null
        val duration = (clip.endSec - clip.startSec).coerceAtLeast(10)
        val content = if (isAss) {
            SubtitleExporter.generateAssKaraoke(clip.transcriptSnippet, duration, clip.subtitleStyle)
        } else {
            SubtitleExporter.generateSrt(clip.transcriptSnippet, duration)
        }
        val ext = if (isAss) "ass" else "srt"
        val file = trimmer.writeSubtitleFile("clip_${clip.id}.$ext", content).getOrNull() ?: return null
        return trimmer.getShareableUri(file)
    }

    fun mineSampleAiNichePattern(niche: String) {
        viewModelScope.launch {
            _nicheMiningStatus.value = AsyncStatus.Loading
            val res = repository.mineSampleAiNichePlaybook(niche)
            res.onSuccess { playbook ->
                _nicheMiningStatus.value = AsyncStatus.Success(playbook)
                _snackbarMessage.value = "Sample AI playbook added (requires Firebase for live Gemini)"
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Niche analysis failed"
                _nicheMiningStatus.value = AsyncStatus.Error(msg)
                _snackbarMessage.value = msg
            }
        }
    }

    fun applyCaptionPresetToStudio(style: SubtitleStyle) {
        _studioState.update { it.copy(subtitleStyle = style) }
        _snackbarMessage.value = "Applied ${style.label} caption style in Studio"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val db = ClipForgeDatabase.getInstance(app)
                val trimmer = LocalVideoTrimmer(app)
                val repo = ClipForgeRepository(
                    db = db,
                    feedClient = YouTubeFeedClient(),
                    aiRepository = PlaceholderAiRepository(),
                    trimmer = trimmer
                )
                ClipForgeViewModel(
                    repository = repo,
                    themePreferences = ThemePreferencesRepository(app),
                    localVideoInspector = LocalVideoInspector(app),
                    localVideoTrimmer = trimmer
                )
            }
        }
    }
}
