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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.NichePlaybookEntity
import com.example.model.AsyncStatus
import com.example.model.NicheLiveAnalytics
import com.example.model.SubtitleStyle
import com.example.ui.components.FirebaseAiNoticeBanner
import com.example.ui.components.StudioCard
import java.util.Locale

private val NICHE_CATEGORIES = listOf("All", "Tech", "Podcasts", "Finance", "Gaming", "Commentary")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NicheIntelScreen(
    liveAnalytics: List<NicheLiveAnalytics>,
    aiPlaybooks: List<NichePlaybookEntity>,
    miningStatus: AsyncStatus<NichePlaybookEntity>,
    onMineSampleAiNiche: (String) -> Unit,
    onApplyCaptionPreset: (SubtitleStyle) -> Unit,
    onOpenTracker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedNiche by remember { mutableStateOf("All") }
    val isMining = miningStatus is AsyncStatus.Loading

    val filteredLive = remember(liveAnalytics, selectedNiche) {
        if (selectedNiche == "All") liveAnalytics
        else liveAnalytics.filter { it.niche.equals(selectedNiche, ignoreCase = true) }
    }

    val filteredAi = remember(aiPlaybooks, selectedNiche) {
        if (selectedNiche == "All") aiPlaybooks
        else aiPlaybooks.filter { it.niche.equals(selectedNiche, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("niche_intel_screen"),
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
                            text = "Niche Intelligence",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Live keyword density, title structure & hook patterns from your RSS feeds",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val target = if (selectedNiche == "All") "Tech" else selectedNiche
                            onMineSampleAiNiche(target)
                        },
                        enabled = !isMining,
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("mine_niche_button")
                    ) {
                        if (isMining) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Insights, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Sample AI")
                    }
                }

                if (aiPlaybooks.isNotEmpty()) {
                    FirebaseAiNoticeBanner()
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NICHE_CATEGORIES.forEach { category ->
                        FilterChip(
                            selected = selectedNiche == category,
                            onClick = { selectedNiche = category },
                            label = { Text(category) }
                        )
                    }
                }
            }
        }

        if (filteredLive.isEmpty() && filteredAi.isEmpty()) {
            item {
                StudioCard(
                    modifier = Modifier
                        .widthIn(max = 840.dp)
                        .fillMaxWidth(),
                    accentBorder = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RssFeed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No live niche data yet",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Add and sync YouTube creators in Trackers to compute real keyword frequencies, average title hook length, and view performance per niche.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenTracker,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Go to Trackers")
                        }
                    }
                }
            }
        }

        items(
            items = filteredLive,
            key = { "live_${it.niche}" }
        ) { insight ->
            StudioCard(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth(),
                accentBorder = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "${insight.niche.uppercase()} · LIVE RSS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = "${formatCompactNumber(insight.avgViews)} avg views",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = insight.topPatternSummary,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Avg Title Length: ${String.format(Locale.US, "%.1f", insight.avgTitleWords)} words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (insight.topKeywords.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            insight.topKeywords.forEach { tag ->
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { onApplyCaptionPreset(SubtitleStyle.HORMOZI) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Apply High-Contrast Caption Preset")
                    }
                }
            }
        }

        items(
            items = filteredAi,
            key = { "ai_${it.id}" }
        ) { playbook ->
            val style = SubtitleStyle.fromRaw(playbook.recommendedCaptionStyle)
            StudioCard(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "${playbook.niche.uppercase()} · SAMPLE AI",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Text(
                        text = playbook.hookFormula,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = playbook.topTagsCsv,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    OutlinedButton(
                        onClick = { onApplyCaptionPreset(style) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Use ${style.label} in Studio")
                    }
                }
            }
        }
    }
}
