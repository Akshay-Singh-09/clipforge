package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.TrackedChannelEntity
import com.example.model.AsyncStatus
import com.example.model.ChannelHealthStatus
import com.example.model.DashboardSummary
import com.example.ui.components.HealthBadge
import com.example.ui.components.StudioCard
import com.example.ui.components.VelocitySparklineChart
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    summary: DashboardSummary,
    channels: List<TrackedChannelEntity>,
    rssSyncStatus: AsyncStatus<Int>,
    onSyncAll: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenTracker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedHealthFilter by remember { mutableStateOf<ChannelHealthStatus?>(null) }
    val isSyncing = rssSyncStatus is AsyncStatus.Loading

    val filteredChannels = remember(channels, selectedHealthFilter) {
        val filter = selectedHealthFilter
        if (filter == null) channels
        else channels.filter { it.healthStatus == filter.name }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Vibrant gradient workstation hero banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Channel Command",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Live YouTube RSS view telemetry & studio exports",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                            if (channels.isNotEmpty()) {
                                Button(
                                    onClick = onSyncAll,
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    if (isSyncing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync")
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenStudio,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black.copy(alpha = 0.25f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dashboard_open_studio_btn")
                            ) {
                                Icon(Icons.Filled.MovieCreation, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clip Studio")
                            }
                            Button(
                                onClick = onOpenTracker,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("dashboard_open_tracker_btn")
                            ) {
                                Icon(Icons.Filled.AddCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Add Creator")
                            }
                        }
                    }
                }

                if (channels.isNotEmpty() || summary.exportedClipCount > 0) {
                    // Colorful Live Telemetry Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        KpiStatCard(
                            title = "Recent Views",
                            value = formatCompactNumber(summary.totalViews),
                            subtitle = "${summary.ingestedVideoCount} RSS videos",
                            icon = Icons.Filled.Visibility,
                            accentColor = MaterialTheme.colorScheme.primary,
                            sparkline = summary.viewSeries,
                            modifier = Modifier.weight(1f)
                        )
                        KpiStatCard(
                            title = "Avg Rating",
                            value = if (summary.avgRating > 0.0) String.format(Locale.US, "%.2f / 5", summary.avgRating) else "—",
                            subtitle = "${summary.exportedClipCount} exported MP4s",
                            icon = Icons.Filled.Star,
                            accentColor = MaterialTheme.colorScheme.secondary,
                            sparkline = emptyList(),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HealthCounterChip(
                            label = "Doing Great",
                            count = summary.doingGreatCount,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        HealthCounterChip(
                            label = "Stable",
                            count = summary.stableCount,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        HealthCounterChip(
                            label = "Attention",
                            count = summary.needsAttentionCount,
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (channels.isNotEmpty()) {
                    Text(
                        text = "Creator Virality Leaderboard",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedHealthFilter == null,
                            onClick = { selectedHealthFilter = null },
                            label = { Text("All (${channels.size})") }
                        )
                        ChannelHealthStatus.entries.forEach { status ->
                            FilterChip(
                                selected = selectedHealthFilter == status,
                                onClick = {
                                    selectedHealthFilter = if (selectedHealthFilter == status) null else status
                                },
                                label = { Text(status.label) }
                            )
                        }
                    }
                }
            }
        }

        if (channels.isEmpty()) {
            item {
                StudioCard(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    accentBorder = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RssFeed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .size(28.dp)
                            )
                        }
                        Text(
                            text = "No channels tracked yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Add any YouTube @handle, channel URL, or UC... ID in Trackers to stream live RSS views, ratings, and creator rankings — or jump straight into Clip Studio to trim a local MP4.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onOpenTracker,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Add Channel")
                            }
                            OutlinedButton(
                                onClick = onOpenStudio,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Filled.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open Studio")
                            }
                        }
                    }
                }
            }
        } else if (filteredChannels.isEmpty()) {
            item {
                StudioCard(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No channels match this health filter",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = filteredChannels,
                key = { _, ch -> ch.id }
            ) { index, channel ->
                CreatorLeaderboardRow(
                    rank = index + 1,
                    channel = channel,
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    sparkline: List<Float>,
    modifier: Modifier = Modifier
) {
    StudioCard(
        modifier = modifier,
        accentBorder = accentColor.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier
                            .padding(5.dp)
                            .size(16.dp)
                    )
                }
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sparkline.size >= 2) {
                VelocitySparklineChart(
                    values = sparkline,
                    lineColor = accentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HealthCounterChip(
    label: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = contentColor
            )
        }
    }
}

@Composable
private fun CreatorLeaderboardRow(
    rank: Int,
    channel: TrackedChannelEntity,
    modifier: Modifier = Modifier
) {
    val health = ChannelHealthStatus.fromRaw(channel.healthStatus)
    StudioCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = channel.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${channel.handle} · ${channel.niche}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HealthBadge(status = health)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricMiniColumn("Avg Views", formatCompactNumber(channel.avgViewsPerVideo))
                MetricMiniColumn("Recent Views", formatCompactNumber(channel.totalRecentViews))
                MetricMiniColumn(
                    "Rating",
                    if (channel.avgStarRating > 0.0) String.format(Locale.US, "%.2f★", channel.avgStarRating) else "—"
                )
                MetricMiniColumn("RSS Videos", "${channel.recentVideoCount}")
            }
        }
    }
}

@Composable
private fun MetricMiniColumn(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun formatCompactNumber(value: Long): String {
    return when {
        value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(Locale.US, "%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}
