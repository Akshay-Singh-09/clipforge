package com.example.ui

import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MovieCreation
import androidx.compose.material.icons.outlined.Publish
import androidx.compose.material.icons.outlined.RssFeed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.model.ThemeMode
import com.example.ui.components.AboutDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NicheIntelScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.TrackerScreen
import com.example.ui.theme.ClipForgeTheme

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_TRACKER = "tracker"
const val ROUTE_STUDIO = "studio"
const val ROUTE_TRENDS = "trends"
const val ROUTE_QUEUE = "queue"

private data class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val APP_DESTINATIONS = listOf(
    NavDestination(ROUTE_DASHBOARD, R.string.nav_dashboard, Icons.Filled.Analytics, Icons.Outlined.Analytics),
    NavDestination(ROUTE_TRACKER, R.string.nav_tracker, Icons.Filled.RssFeed, Icons.Outlined.RssFeed),
    NavDestination(ROUTE_STUDIO, R.string.nav_studio, Icons.Filled.MovieCreation, Icons.Outlined.MovieCreation),
    NavDestination(ROUTE_TRENDS, R.string.nav_trends, Icons.Filled.Insights, Icons.Outlined.Insights),
    NavDestination(ROUTE_QUEUE, R.string.nav_queue, Icons.Filled.Publish, Icons.Outlined.Publish)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipForgeApp(
    viewModel: ClipForgeViewModel = viewModel(factory = ClipForgeViewModel.Factory)
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    LaunchedEffect(useDarkTheme, view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>()
            .firstOrNull()
        activity?.enableEdgeToEdge(
            statusBarStyle = if (useDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            },
            navigationBarStyle = if (useDarkTheme) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            }
        )
    }

    val dashboardSummary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val channels by viewModel.trackedChannels.collectAsStateWithLifecycle()
    val ingestedVideos by viewModel.ingestedVideos.collectAsStateWithLifecycle()
    val clips by viewModel.generatedClips.collectAsStateWithLifecycle()
    val liveNicheAnalytics by viewModel.liveNicheAnalytics.collectAsStateWithLifecycle()
    val playbooks by viewModel.nichePlaybooks.collectAsStateWithLifecycle()
    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val rssSyncStatus by viewModel.rssSyncStatus.collectAsStateWithLifecycle()
    val nicheMiningStatus by viewModel.nicheMiningStatus.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    ClipForgeTheme(darkTheme = useDarkTheme) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: ROUTE_DASHBOARD
        val snackbarHostState = remember { SnackbarHostState() }
        var showAboutDialog by remember { mutableStateOf(false) }

        LaunchedEffect(snackbarMessage) {
            val msg = snackbarMessage
            if (msg != null) {
                snackbarHostState.showSnackbar(msg)
                viewModel.consumeSnackbar()
            }
        }

        val navigateToTab: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "STUDIO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.cycleThemeMode(useDarkTheme) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (useDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                    contentDescription = stringResource(R.string.cd_toggle_theme)
                                )
                            }
                            IconButton(
                                onClick = { showAboutDialog = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("about_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = stringResource(R.string.cd_open_about)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        )
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            val navColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            APP_DESTINATIONS.forEach { dest ->
                                val selected = currentRoute == dest.route
                                val label = stringResource(dest.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(dest.route) },
                                    colors = navColors,
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            val railColors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                            APP_DESTINATIONS.forEach { dest ->
                                val selected = currentRoute == dest.route
                                val label = stringResource(dest.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTab(dest.route) },
                                    colors = railColors,
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_DASHBOARD
                        ) {
                            composable(ROUTE_DASHBOARD) {
                                DashboardScreen(
                                    summary = dashboardSummary,
                                    channels = channels,
                                    rssSyncStatus = rssSyncStatus,
                                    onSyncAll = viewModel::syncAllChannels,
                                    onOpenStudio = { navigateToTab(ROUTE_STUDIO) },
                                    onOpenTracker = { navigateToTab(ROUTE_TRACKER) }
                                )
                            }
                            composable(ROUTE_TRACKER) {
                                TrackerScreen(
                                    channels = channels,
                                    ingestedVideos = ingestedVideos,
                                    rssSyncStatus = rssSyncStatus,
                                    onAddChannel = viewModel::addChannel,
                                    onToggleMonitor = viewModel::toggleChannelMonitor,
                                    onDeleteChannel = viewModel::deleteChannel,
                                    onSyncAll = viewModel::syncAllChannels,
                                    onSyncChannel = viewModel::syncSingleChannel,
                                    onClipInStudio = { video ->
                                        viewModel.sendRssVideoToStudio(video)
                                        navigateToTab(ROUTE_STUDIO)
                                    }
                                )
                            }
                            composable(ROUTE_STUDIO) {
                                StudioScreen(
                                    studioState = studioState,
                                    clips = clips,
                                    onUrlChange = viewModel::updateStudioSourceUrl,
                                    onFetchOEmbed = viewModel::fetchVideoMetadataFromUrl,
                                    onPickLocalVideo = viewModel::loadLocalPickedVideo,
                                    onClipTitleChange = viewModel::updateClipTitleInput,
                                    onCaptionScriptChange = viewModel::updateCaptionScriptInput,
                                    onStickerTextChange = viewModel::updateStickerTextInput,
                                    onTrimRangeChange = viewModel::updateTrimRange,
                                    onFramingModeChange = viewModel::updateFramingMode,
                                    onSubtitleStyleChange = viewModel::updateSubtitleStyle,
                                    onLoudnessChange = viewModel::updateLoudnessNormalized,
                                    onSilenceTrimChange = viewModel::updateSilenceTrimmed,
                                    onCreateAndTrimClip = viewModel::createAndTrimClip,
                                    onSelectClip = viewModel::selectClipForPreview,
                                    onSaveClipEdits = viewModel::saveClipEdits,
                                    onReExportMp4 = viewModel::reExportClipMp4,
                                    onAttachLocalVideoToClip = viewModel::attachLocalVideoToExistingClip,
                                    onSaveMp4ToGallery = viewModel::saveClipMp4ToGallery,
                                    onCopyMp4ToUri = viewModel::copyClipMp4ToUri,
                                    onRunSampleAiScore = viewModel::runSampleAiHookScore,
                                    getShareableMp4Uri = viewModel::getShareableMp4Uri,
                                    exportSubtitleUri = viewModel::exportSubtitleFileUri,
                                    onShowMessage = viewModel::showMessage
                                )
                            }
                            composable(ROUTE_TRENDS) {
                                NicheIntelScreen(
                                    liveAnalytics = liveNicheAnalytics,
                                    aiPlaybooks = playbooks,
                                    miningStatus = nicheMiningStatus,
                                    onMineSampleAiNiche = viewModel::mineSampleAiNichePattern,
                                    onApplyCaptionPreset = { style ->
                                        viewModel.applyCaptionPresetToStudio(style)
                                        navigateToTab(ROUTE_STUDIO)
                                    },
                                    onOpenTracker = { navigateToTab(ROUTE_TRACKER) }
                                )
                            }
                            composable(ROUTE_QUEUE) {
                                QueueScreen(
                                    clips = clips,
                                    getShareableMp4Uri = viewModel::getShareableMp4Uri,
                                    exportSubtitleUri = viewModel::exportSubtitleFileUri,
                                    onSaveMp4ToGallery = viewModel::saveClipMp4ToGallery,
                                    onCopyMp4ToUri = viewModel::copyClipMp4ToUri,
                                    onReExportMp4 = viewModel::reExportClipMp4,
                                    onUpdateUploadState = viewModel::queueClipForUpload,
                                    onDeleteClip = viewModel::deleteClip,
                                    onOpenStudio = { navigateToTab(ROUTE_STUDIO) },
                                    onShowMessage = viewModel::showMessage
                                )
                            }
                        }
                    }
                }
            }

            if (showAboutDialog) {
                AboutDialog(
                    currentThemeMode = themeMode,
                    onSelectThemeMode = viewModel::setThemeMode,
                    onDismiss = { showAboutDialog = false }
                )
            }
        }
    }
}
