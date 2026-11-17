package com.example.ui

import android.content.Intent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.model.KerasArchitectureParser
import com.example.data.repository.ClassificationAiRepository
import com.example.data.repository.ConservationRepository
import com.example.firebase.authStateFlow
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClassifyScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LogbookScreen
import com.example.ui.screens.SettingsAndAccountDialog
import com.example.ui.screens.YieldScreen
import com.example.ui.viewmodel.LepVisionViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private enum class LepDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", "Sanctuary", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    CLASSIFY("classify", "Classify", Icons.Filled.CameraAlt, Icons.Outlined.CameraAlt),
    YIELD("yield", "Yield", Icons.Filled.Assessment, Icons.Outlined.Assessment),
    LOGBOOK("logbook", "Logbook", Icons.Filled.Book, Icons.Outlined.Book)
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            AuthenticatedLepVisionShell(
                currentUserId = user.uid,
                userEmail = user.email
            )
        }
    }
}

@Composable
private fun AuthenticatedLepVisionShell(
    currentUserId: String,
    userEmail: String?
) {
    val viewModel: LepVisionViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val kerasSpecs = runCatching {
                    KerasArchitectureParser.loadRegistryFromAssets(app)
                }.getOrDefault(emptyMap())
                LepVisionViewModel(
                    conservationRepository = ConservationRepository(db),
                    aiRepository = ClassificationAiRepository(),
                    currentUserId = currentUserId,
                    kerasSpecs = kerasSpecs
                )
            }
        }
    )

    val classificationsState by viewModel.classificationsState.collectAsStateWithLifecycle()
    val batchesState by viewModel.batchesState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferencesState.collectAsStateWithLifecycle()
    val selectedTask by viewModel.selectedTask.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val serverTestState by viewModel.serverTestState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: LepDestination.DASHBOARD.route

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun triggerCsvExport() {
        scope.launch {
            val uri = viewModel.exportManifestCsvUri(context)
            if (uri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "LepVision Dataset Manifest (Marinduque)")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Export Dataset Manifest"))
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        LepDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
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
                    NavigationRail(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        LepDestination.entries.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = LepDestination.DASHBOARD.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(LepDestination.DASHBOARD.route) {
                        DashboardScreen(
                            userEmail = userEmail,
                            classificationsState = classificationsState,
                            batchesState = batchesState,
                            preferences = preferences,
                            kerasSpecs = viewModel.kerasSpecs,
                            onNavigateToClassify = { task ->
                                viewModel.selectTask(task)
                                navigateTo(LepDestination.CLASSIFY.route)
                            },
                            onNavigateToYield = {
                                navigateTo(LepDestination.YIELD.route)
                            },
                            onOpenModelSettings = { showSettingsDialog = true },
                            onExportCsv = ::triggerCsvExport
                        )
                    }
                    composable(LepDestination.CLASSIFY.route) {
                        ClassifyScreen(
                            selectedTask = selectedTask,
                            scanState = scanState,
                            preferences = preferences,
                            kerasSpecs = viewModel.kerasSpecs,
                            onSelectTask = viewModel::selectTask,
                            onAnalyzeUri = viewModel::analyzeImageUri,
                            onRetryAnalysis = viewModel::retryLastAnalysis,
                            onSaveResult = viewModel::saveCurrentScanResult,
                            onClearScan = viewModel::clearScan,
                            onOpenSettings = { showSettingsDialog = true }
                        )
                    }
                    composable(LepDestination.YIELD.route) {
                        YieldScreen(
                            batchesState = batchesState,
                            classificationsState = classificationsState,
                            onSaveBatch = viewModel::saveBatch,
                            onDeleteBatch = viewModel::deleteBatch
                        )
                    }
                    composable(LepDestination.LOGBOOK.route) {
                        LogbookScreen(
                            classificationsState = classificationsState,
                            onUpdateRecordNotes = viewModel::updateRecordNotes,
                            onDeleteRecord = viewModel::deleteRecord,
                            onOpenSettings = { showSettingsDialog = true },
                            onExportCsv = ::triggerCsvExport
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsAndAccountDialog(
            userEmail = userEmail,
            preferences = preferences,
            kerasSpecs = viewModel.kerasSpecs,
            serverTestState = serverTestState,
            onTestCnnServer = viewModel::testCnnServer,
            onClearServerTestState = viewModel::clearServerTestState,
            onDismiss = { showSettingsDialog = false },
            onSavePreferences = { mode, url, folderId, dirPath, modelFile, conf ->
                viewModel.savePreferences(
                    engineMode = mode,
                    customCnnUrl = url,
                    driveFolderId = folderId,
                    driveDirectoryPath = dirPath,
                    speciesModelFile = modelFile,
                    minConfidence = conf
                )
                showSettingsDialog = false
            }
        )
    }
}
