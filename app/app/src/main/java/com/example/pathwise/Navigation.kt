package com.example.pathwise

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.pathwise.data.model.Journey
import com.example.pathwise.data.model.LocationPoint
import com.example.pathwise.data.repository.JourneyRepository
import com.example.pathwise.data.repository.LandmarkRepository
import com.example.pathwise.service.JourneyTrackingService
import com.example.pathwise.theme.EffectsLevel
import com.example.pathwise.theme.LocalEffects
import com.example.pathwise.theme.PathWiseEffects
import com.example.pathwise.ui.dialogs.PermissionDialog
import com.example.pathwise.ui.dialogs.RecoveryDialog
import com.example.pathwise.ui.home.HomeScreen
import com.example.pathwise.ui.journeys.JourneyDetailScreen
import com.example.pathwise.ui.journeys.JourneysScreen
import com.example.pathwise.ui.learn.LearnScreen
import com.example.pathwise.ui.map.MapScreen
import com.example.pathwise.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

// ── Screen Definitions ────────────────────────────────────────────────────────
sealed class Screen {
    data object Home : Screen()
    data object Map : Screen()
    data object Journeys : Screen()
    data class JourneyDetail(val journeyId: String) : Screen()
    data object Learn : Screen()
    data object Settings : Screen()
}

@Composable
fun MainNavigation() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val journeyRepo = remember { JourneyRepository(context) }
    val landmarkRepo = remember { LandmarkRepository(context) }

    // State collections
    val journeys by journeyRepo.journeysList.collectAsState()
    val activeJourney by JourneyTrackingService.currentJourney.collectAsState()
    val trackingState by JourneyTrackingService.trackingState.collectAsState()
    val currentLocation by JourneyTrackingService.currentLocation.collectAsState()
    val activePoints by journeyRepo.activePoints.collectAsState()
    val landmarks by landmarkRepo.landmarks.collectAsState()

    // Effects Mode state (Cinematic / Balanced / Reduced)
    var effectsMode by remember { mutableStateOf(EffectsLevel.BALANCED) }

    // Dialog states
    var showPermissionDialog by remember { mutableStateOf(false) }
    var interruptedJourney by remember { mutableStateOf<Journey?>(null) }

    // Backstack management: stable, zero-dependency, crash-free
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.size - 1)
    }

    // Initial data refresh & interrupted journey check
    LaunchedEffect(Unit) {
        try {
            journeyRepo.refreshJourneys()
            landmarkRepo.refresh()
            landmarkRepo.seedDemoLandmarks()

            val interrupted = journeyRepo.checkInterruptedJourney()
            if (interrupted != null) {
                interruptedJourney = interrupted
            }
        } catch (e: Throwable) {
            android.util.Log.e("PathWise", "Database init warning: ${e.message}", e)
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            val intent = Intent(context, JourneyTrackingService::class.java).apply {
                action = JourneyTrackingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            backStack.add(Screen.Map)
        }
    }

    fun hasLocationPermissions(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun requestTrackingStart() {
        if (hasLocationPermissions()) {
            val intent = Intent(context, JourneyTrackingService::class.java).apply {
                action = JourneyTrackingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            backStack.add(Screen.Map)
        } else {
            showPermissionDialog = true
        }
    }

    // Wrap entire navigation in dynamic EffectsMode
    CompositionLocalProvider(LocalEffects provides PathWiseEffects(effectsMode)) {
        Box(modifier = Modifier.fillMaxSize()) {
            val currentScreen = backStack.lastOrNull() ?: Screen.Home

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(animationSpec = tween(180)))
                },
                label = "HoloNavTransition"
            ) { screen ->
                when (screen) {
                    is Screen.Home -> {
                        val totalDist = journeys.sumOf { it.recordedDistanceMeters }
                        HomeScreen(
                            journeysCount = journeys.size,
                            landmarksCount = landmarks.size,
                            totalDistanceMeters = totalDist,
                            activeJourney = activeJourney,
                            recentJourneys = journeys,
                            onSelectJourney = { selected ->
                                backStack.add(Screen.JourneyDetail(selected.id))
                            },
                            onStartJourney = { requestTrackingStart() },
                            onStartDemo = {
                                try {
                                    val intent = Intent(context, JourneyTrackingService::class.java).apply {
                                        action = JourneyTrackingService.ACTION_SIMULATE_DEMO
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                } catch (e: Throwable) {
                                    android.util.Log.e("PathWise", "startDemo error handled: ${e.message}", e)
                                }
                                backStack.add(Screen.Map)
                            },
                            onExploreMap = { backStack.add(Screen.Map) },
                            onOpenJourneys = { backStack.add(Screen.Journeys) },
                            onOpenLearn = { backStack.add(Screen.Learn) },
                            onOpenSettings = { backStack.add(Screen.Settings) }
                        )
                    }

                    is Screen.Map -> {
                        MapScreen(
                            trackingState = trackingState,
                            currentJourney = activeJourney,
                            points = activePoints,
                            landmarks = landmarks,
                            currentLocation = currentLocation,
                            onPause = {
                                val intent = Intent(context, JourneyTrackingService::class.java).apply {
                                    action = JourneyTrackingService.ACTION_PAUSE
                                }
                                context.startService(intent)
                            },
                            onResume = {
                                val intent = Intent(context, JourneyTrackingService::class.java).apply {
                                    action = JourneyTrackingService.ACTION_RESUME
                                }
                                context.startService(intent)
                            },
                            onStop = {
                                val intent = Intent(context, JourneyTrackingService::class.java).apply {
                                    action = JourneyTrackingService.ACTION_STOP
                                }
                                context.startService(intent)
                            },
                            onSaveLandmark = { name, cat, notes, lat, lon ->
                                coroutineScope.launch {
                                    landmarkRepo.addLandmark(name, cat, notes, lat, lon)
                                }
                            },
                            onNavigateBack = {
                                if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                            }
                        )
                    }

                    is Screen.Journeys -> {
                        JourneysScreen(
                            journeys = journeys,
                            onSelectJourney = { selected ->
                                backStack.add(Screen.JourneyDetail(selected.id))
                            },
                            onDeleteJourney = { id ->
                                coroutineScope.launch {
                                    journeyRepo.discardJourney(id)
                                }
                            },
                            onNavigateBack = {
                                if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                            }
                        )
                    }

                    is Screen.JourneyDetail -> {
                        var detailJourney by remember(screen.journeyId) { mutableStateOf<Journey?>(null) }
                        var detailPoints by remember(screen.journeyId) { mutableStateOf<List<LocationPoint>>(emptyList()) }

                        LaunchedEffect(screen.journeyId) {
                            val (j, pts) = journeyRepo.getJourneyDetails(screen.journeyId)
                            detailJourney = j
                            detailPoints = pts
                        }

                        if (detailJourney != null) {
                            JourneyDetailScreen(
                                journey = detailJourney!!,
                                points = detailPoints,
                                onNavigateBack = {
                                    if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                }
                            )
                        }
                    }

                    is Screen.Learn -> {
                        LearnScreen(
                            landmarks = landmarks,
                            onNavigateBack = {
                                if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                            }
                        )
                    }

                    is Screen.Settings -> {
                        SettingsScreen(
                            currentMode = effectsMode,
                            onModeChange = { effectsMode = it },
                            onSeedDemo = {
                                coroutineScope.launch {
                                    journeyRepo.seedDemoJourney()
                                    landmarkRepo.seedDemoLandmarks()
                                }
                            },
                            onNavigateBack = {
                                if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                            }
                        )
                    }
                }
            }

            // Contextual Permission Dialog
            if (showPermissionDialog) {
                PermissionDialog(
                    onDismiss = { showPermissionDialog = false },
                    onConfirm = {
                        showPermissionDialog = false
                        val perms = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(perms.toTypedArray())
                    }
                )
            }

            // Interrupted Journey Recovery Dialog
            interruptedJourney?.let { interrupted ->
                RecoveryDialog(
                    interruptedJourney = interrupted,
                    onResume = {
                        val jId = interrupted.id
                        interruptedJourney = null
                        coroutineScope.launch {
                            journeyRepo.recoverInterruptedJourney(jId)
                            val intent = Intent(context, JourneyTrackingService::class.java).apply {
                                action = JourneyTrackingService.ACTION_START
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            backStack.add(Screen.Map)
                        }
                    },
                    onFinalise = {
                        val jId = interrupted.id
                        interruptedJourney = null
                        coroutineScope.launch {
                            journeyRepo.stopJourney(jId)
                        }
                    },
                    onDiscard = {
                        val jId = interrupted.id
                        interruptedJourney = null
                        coroutineScope.launch {
                            journeyRepo.discardJourney(jId)
                        }
                    }
                )
            }
        }
    }
}
