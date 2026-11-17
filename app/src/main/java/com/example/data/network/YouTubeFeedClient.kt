package com.example.data.network

import android.util.Xml
import com.example.model.ResolvedChannelInfo
import com.example.model.RssVideoItem
import com.example.model.VideoOEmbedInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YouTubeFeedClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    suspend fun resolveChannelInput(rawInput: String): Result<ResolvedChannelInfo> =
        withContext(Dispatchers.IO) {
            try {
                val trimmed = rawInput.trim()
                if (trimmed.isBlank()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Enter a YouTube @handle, channel URL, or UC... Channel ID")
                    )
                }

                // Direct UC... Channel ID
                extractChannelId(trimmed)?.let { directId ->
                    return@withContext Result.success(
                        ResolvedChannelInfo(
                            channelId = directId,
                            displayName = "",
                            handle = ""
                        )
                    )
                }

                // Resolve @handle or youtube.com/@handle or custom URL by fetching channel page metadata
                val handleNormalized = when {
                    trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
                    trimmed.startsWith("@") -> "https://www.youtube.com/$trimmed"
                    else -> "https://www.youtube.com/@${trimmed.removePrefix("@")}"
                }

                val request = Request.Builder()
                    .url(handleNormalized)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Could not resolve channel (HTTP ${response.code})")
                        )
                    }
                    val html = response.body?.string().orEmpty()
                    val foundId = extractChannelIdFromHtml(html)
                        ?: return@withContext Result.failure(
                            IllegalStateException("Could not find Channel ID on page. Paste the UC... Channel ID directly.")
                        )
                    val foundTitle = extractMetaTitleFromHtml(html)
                    val foundHandle = if (trimmed.contains("@")) {
                        "@" + trimmed.substringAfter("@").substringBefore("/").substringBefore("?")
                    } else ""
                    Result.success(
                        ResolvedChannelInfo(
                            channelId = foundId,
                            displayName = foundTitle,
                            handle = foundHandle
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchChannelRssFeed(channelId: String): Result<List<RssVideoItem>> =
        withContext(Dispatchers.IO) {
            try {
                val cleanId = extractChannelId(channelId)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Invalid YouTube Channel ID (must start with UC...)")
                    )
                val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$cleanId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ClipForge/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("YouTube RSS returned HTTP ${response.code}")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        return@withContext Result.failure(IllegalStateException("Empty RSS feed response"))
                    }
                    Result.success(parseChannelRssXml(body, cleanId))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchVideoOEmbed(rawInput: String): Result<VideoOEmbedInfo> =
        withContext(Dispatchers.IO) {
            try {
                val videoId = extractYouTubeVideoId(rawInput)
                    ?: return@withContext Result.failure(
                        IllegalArgumentException("Enter a valid YouTube video URL (youtube.com/watch?v=... or youtu.be/...)")
                    )
                val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
                val encodedUrl = URLEncoder.encode(canonicalUrl, "UTF-8")
                val oembedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

                val request = Request.Builder()
                    .url(oembedUrl)
                    .header("User-Agent", "ClipForge/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Video metadata unavailable (HTTP ${response.code})")
                        )
                    }
                    val body = response.body?.string().orEmpty()
                    val json = JSONObject(body)
                    Result.success(
                        VideoOEmbedInfo(
                            videoUrl = canonicalUrl,
                            title = json.optString("title", "YouTube Video $videoId"),
                            authorName = json.optString("author_name", "YouTube Creator"),
                            thumbnailUrl = json.optString(
                                "thumbnail_url",
                                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            )
                        )
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    companion object {
        private val CHANNEL_ID_REGEX = Regex("UC[a-zA-Z0-9_-]{20,24}")
        private val HTML_CHANNEL_REGEX = Regex("(?:externalId|channelId)\"\\s*:\\s*\"(UC[a-zA-Z0-9_-]{20,24})\"")
        private val HTML_RSS_REGEX = Regex("feeds/videos\\.xml\\?channel_id=(UC[a-zA-Z0-9_-]{20,24})")
        private val HTML_CANONICAL_REGEX = Regex("youtube\\.com/channel/(UC[a-zA-Z0-9_-]{20,24})")
        private val HTML_META_TITLE_REGEX = Regex("<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']+)[\"']")
        private val WATCH_V_REGEX = Regex("[?&]v=([a-zA-Z0-9_-]{11})")
        private val SHORT_PATH_REGEX = Regex("(?:youtu\\.be/|youtube\\.com/(?:shorts|embed|live)/)([a-zA-Z0-9_-]{11})")
        private val BARE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

        fun extractChannelId(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return null
            return CHANNEL_ID_REGEX.find(trimmed)?.value
        }

        fun extractChannelIdFromHtml(html: String): String? {
            HTML_RSS_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { return it }
            HTML_CHANNEL_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { return it }
            HTML_CANONICAL_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }

        private fun extractMetaTitleFromHtml(html: String): String {
            val raw = HTML_META_TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            return raw
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
        }

        fun extractYouTubeVideoId(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isEmpty()) return null
            if (BARE_VIDEO_ID_REGEX.matches(trimmed)) return trimmed
            WATCH_V_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            SHORT_PATH_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { return it }
            return null
        }

        fun parseChannelRssXml(xml: String, channelIdFallback: String): List<RssVideoItem> {
            if (xml.isBlank()) return emptyList()
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            val results = mutableListOf<RssVideoItem>()
            var feedAuthor = ""
            var inEntry = false
            var inAuthor = false
            var currentTag = ""

            var videoId = ""
            var channelId = channelIdFallback
            var title = ""
            var link = ""
            var published = ""
            var author = ""
            var thumbnailUrl = ""
            var viewCount = 0L
            var starRating = 0.0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val name = parser.name.orEmpty()
                        currentTag = name
                        when (name) {
                            "entry" -> {
                                inEntry = true
                                videoId = ""
                                channelId = channelIdFallback
                                title = ""
                                link = ""
                                published = ""
                                author = feedAuthor
                                thumbnailUrl = ""
                                viewCount = 0L
                                starRating = 0.0
                            }
                            "author" -> inAuthor = true
                            "link" -> {
                                if (inEntry) {
                                    val href = parser.getAttributeValue(null, "href").orEmpty()
                                    if (href.isNotBlank()) link = href
                                }
                            }
                            "media:thumbnail" -> {
                                if (inEntry) {
                                    val url = parser.getAttributeValue(null, "url").orEmpty()
                                    if (url.isNotBlank()) thumbnailUrl = url
                                }
                            }
                            "media:statistics" -> {
                                if (inEntry) {
                                    viewCount = parser.getAttributeValue(null, "views")
                                        ?.toLongOrNull() ?: 0L
                                }
                            }
                            "media:starRating" -> {
                                if (inEntry) {
                                    starRating = parser.getAttributeValue(null, "average")
                                        ?.toDoubleOrNull() ?: 0.0
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text.orEmpty().trim()
                        if (text.isNotEmpty()) {
                            if (!inEntry) {
                                if (inAuthor && currentTag == "name") {
                                    feedAuthor = text
                                }
                            } else {
                                when (currentTag) {
                                    "yt:videoId" -> videoId = text
                                    "yt:channelId" -> channelId = text
                                    "title" -> title = text
                                    "published" -> published = text
                                    "name" -> if (inAuthor) author = text
                                }
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "author" -> inAuthor = false
                            "entry" -> {
                                if (videoId.isNotBlank() && title.isNotBlank()) {
                                    val resolvedLink = link.ifBlank {
                                        "https://www.youtube.com/watch?v=$videoId"
                                    }
                                    val resolvedThumb = thumbnailUrl.ifBlank {
                                        "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                                    }
                                    results.add(
                                        RssVideoItem(
                                            videoId = videoId,
                                            channelId = channelId,
                                            channelName = author.ifBlank { "YouTube Creator" },
                                            title = title,
                                            videoUrl = resolvedLink,
                                            thumbnailUrl = resolvedThumb,
                                            publishedIso = published,
                                            viewCount = viewCount,
                                            starRating = starRating
                                        )
                                    )
                                }
                                inEntry = false
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
            return results
        }
    }
}
