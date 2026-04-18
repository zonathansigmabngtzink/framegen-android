package com.frameinterpolator.presentation.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.frameinterpolator.data.model.TopLevelDestination
import com.frameinterpolator.presentation.viewmodel.MainViewModel
import com.frameinterpolator.util.Formatters

private data class BottomDestination(
    val route: String,
    val destination: TopLevelDestination,
    val icon: ImageVector,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController = rememberNavController()

    val videoMetadata by viewModel.videoMetadata.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val queueState by viewModel.queueState.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val history by viewModel.history.collectAsState()
    val preferences by viewModel.preferences.collectAsState()
    val targetFps by viewModel.targetFps.collectAsState()
    val outputSize by viewModel.outputSize.collectAsState()
    val quality by viewModel.quality.collectAsState()
    val interpolationMode by viewModel.interpolationMode.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()

    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(viewModel::onVideoSelected)
    }

    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        viewModel.queueSelectedUris(uris)
    }

    val destinations = remember {
        listOf(
            BottomDestination("home", TopLevelDestination.Home, Icons.Default.Home, "Ana Sayfa"),
            BottomDestination("presets", TopLevelDestination.Presets, Icons.Default.AutoAwesome, "Hazırlar"),
            BottomDestination("exports", TopLevelDestination.Exports, Icons.Default.FolderOpen, "Çıktılar"),
            BottomDestination("settings", TopLevelDestination.Settings, Icons.Default.Settings, "Ayarlar")
        )
    }

    fun selectVideo() {
        singlePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    fun selectMultiple() {
        multiPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
    }

    fun openVideo(uri: Uri) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    fun shareVideo(uri: Uri) {
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Videoyu paylaş"
            )
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column {
                        Text("FrameForge")
                        Text(
                            text = "AI destekli video akıcılık stüdyosu",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            val entry by navController.currentBackStackEntryAsState()
            val currentRoute = entry?.destination?.route

            NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
                destinations.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.24f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                composable("home") {
                    HomeRouteScreen(
                        videoMetadata = videoMetadata,
                        processingState = processingState,
                        targetFps = targetFps,
                        outputLabel = outputSize?.label,
                        quality = quality,
                        interpolationMode = interpolationMode,
                        selectedPreset = selectedPreset,
                        estimateLabel = viewModel.currentEstimate()?.let {
                            "${Formatters.formatFileSize(it.estimatedSizeBytes)} • yaklaşık ${Formatters.formatDuration(it.estimatedDurationMillis)}"
                        },
                        availableFps = viewModel.getAvailableFps(),
                        availableSizes = viewModel.getAvailableOutputSizes(),
                        showLowFpsWarning = viewModel.isLowSourceFps(),
                        onSelectVideo = ::selectVideo,
                        onSelectMultiple = ::selectMultiple,
                        onSetTargetFps = viewModel::setTargetFps,
                        onSetInterpolationMode = viewModel::setInterpolationMode,
                        onApplyCustomFps = viewModel::applyCustomTargetFps,
                        validateCustomFps = viewModel::validateCustomTargetFps,
                        onSetOutputSize = viewModel::setOutputSize,
                        onSetQuality = viewModel::setQuality,
                        onStartProcessing = viewModel::startProcessing,
                        onCancelProcessing = viewModel::cancelProcessing,
                        onDismissProcessing = viewModel::resetProcessing,
                        onOpenVideo = ::openVideo,
                        onShareVideo = ::shareVideo
                    )
                }

                composable("presets") {
                    PresetsRouteScreen(
                        presets = viewModel.builtInPresets(),
                        selectedPreset = selectedPreset,
                        sourceName = videoMetadata?.name,
                        onSelectVideo = ::selectVideo,
                        onApplyPreset = viewModel::applyPreset
                    )
                }

                composable("exports") {
                    ExportsRouteScreen(
                        queueState = queueState,
                        processingState = processingState,
                        diagnostics = diagnostics.takeIf { preferences.enableDetailedDiagnostics },
                        history = history,
                        onSelectMultiple = ::selectMultiple,
                        onRefreshHistory = viewModel::refreshHistory,
                        onClearQueue = viewModel::clearQueue,
                        onClearDiagnostics = viewModel::clearDiagnostics,
                        onCancelExport = viewModel::cancelProcessing,
                        onDismissProcessing = viewModel::resetProcessing,
                        onOpenVideo = ::openVideo,
                        onShareVideo = ::shareVideo
                    )
                }

                composable("settings") {
                    SettingsRouteScreen(
                        preferences = preferences,
                        selectedQuality = preferences.defaultQuality,
                        onQualityChanged = viewModel::updateDefaultQuality,
                        onNotifyChanged = viewModel::updateNotifyOnCompletion,
                        onRememberSelectionsChanged = viewModel::updateRememberSelections,
                        onDiagnosticsChanged = viewModel::updateDetailedDiagnostics
                    )
                }
            }
        }
    }
}
