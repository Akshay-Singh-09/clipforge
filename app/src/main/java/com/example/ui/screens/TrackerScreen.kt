package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.IngestedVideoEntity
import com.example.data.local.TrackedChannelEntity
import com.example.model.AsyncStatus
import com.example.model.ChannelHealthStatus
import com.example.ui.components.HealthBadge
import com.example.ui.components.StudioCard

private val CREATOR_NICHES = listOf("Tech", "Podcasts", "Finance", "Gaming", "Commentary")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackerScreen(
    channels: List<TrackedChannelEntity>,
    ingestedVideos: List<IngestedVideoEntity>,
    rssSyncStatus: AsyncStatus<Int>,
    onAddChannel: (channelInput: String, displayName: String, niche: String) -> Unit,
    onToggleMonitor: (Long, Boolean) -> Unit,
    onDeleteChannel: (TrackedChannelEntity) -> Unit,
    onSyncAll: () -> Unit,
    onSyncChannel: (String) -> Unit,
    onClipInStudio: (IngestedVideoEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var channelInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var selectedNiche by remember { mutableStateOf("Tech") }

    val isSyncing = rssSyncStatus is AsyncStatus.Loading

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("tracker_screen"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Channel Tracker",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Resolve any @handle, channel URL, or UC... ID & stream live RSS",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (channels.isNotEmpty()) {
                        Button(
                            onClick = onSyncAll,
                            enabled = !isSyncing,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("sync_all_rss_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSyncing) "Syncing" else "Sync All")
                        }
                    }
                }

                StudioCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentBorder = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Add YouTube Creator",
                            style = MaterialTheme.typography.titleMedium
                        )

                        OutlinedTextField(
                            value = channelInput,
                            onValueChange = { channelInput = it },
                            label = { Text("@Handle, Channel URL, or UC... ID") },
                            placeholder = { Text("@mkbhd or https://youtube.com/@veritasium") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("channel_id_input")
                        )

                        OutlinedTextField(
                            value = displayNameInput,
                            onValueChange = { displayNameInput = it },
                            label = { Text("Display Name (optional — auto-detected)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("channel_name_input")
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CREATOR_NICHES.forEach { niche ->
                                FilterChip(
                                    selected = selectedNiche == niche,
                                    onClick = { selectedNiche = niche },
                                    label = { Text(niche) }
                                )
                            }
                        }

                        Button(
                            onClick = {
                                onAddChannel(channelInput, displayNameInput, selectedNiche)
                                channelInput = ""
                                displayNameInput = ""
                            },
                            enabled = channelInput.isNotBlank() && !isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("add_channel_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (isSyncing) "Resolving & Syncing..." else "Resolve & Sync Live RSS")
                        }
                    }
                }

                if (channels.isNotEmpty()) {
                    Text(
                        text = "Monitored Creators (${channels.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }

        items(
            items = channels,
            key = { it.id }
        ) { channel ->
            StudioCard(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = channel.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${channel.handle} · ${channel.niche} · ${formatCompactNumber(channel.avgViewsPerVideo)} avg views",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        HealthBadge(status = ChannelHealthStatus.fromRaw(channel.healthStatus))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = channel.autoMonitor,
                                onCheckedChange = { onToggleMonitor(channel.id, it) }
                            )
                            Text(
                                text = if (channel.autoMonitor) "Auto-poll RSS" else "Paused",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onSyncChannel(channel.channelId) },
                                enabled = !isSyncing,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Filled.RssFeed, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Refresh")
                            }
                            IconButton(
                                onClick = { onDeleteChannel(channel) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove ${channel.displayName}"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (ingestedVideos.isNotEmpty()) {
            item {
                Text(
                    text = "Live RSS Videos (${ingestedVideos.size})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                )
            }

            items(
                items = ingestedVideos,
                key = { it.videoId }
            ) { video ->
                StudioCard(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = video.thumbnailUrl,
                            contentDescription = "Thumbnail for ${video.title}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 96.dp, height = 60.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = video.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "${formatCompactNumber(video.viewCount)} views",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "${video.channelName} · ${video.publishedIso.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { onClipInStudio(video) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (video.isQueuedToStudio) Icons.Filled.Check else Icons.Filled.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (video.isQueuedToStudio) "Loaded" else "Clip")
                        }
                    }
                }
            }
        }
    }
}
