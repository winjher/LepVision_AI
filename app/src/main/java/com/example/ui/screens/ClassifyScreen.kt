package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.camera.CameraHelper
import com.example.data.model.ClassificationTask
import com.example.data.model.ClassificationTaxonomy
import com.example.data.model.KerasModelSpec
import com.example.data.model.UserPreferences
import com.example.ui.viewmodel.ScanUiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClassifyScreen(
    selectedTask: ClassificationTask,
    scanState: ScanUiState,
    preferences: UserPreferences,
    kerasSpecs: Map<ClassificationTask, KerasModelSpec> = emptyMap(),
    onSelectTask: (ClassificationTask) -> Unit,
    onAnalyzeUri: (Context, Uri, String?) -> Unit,
    onRetryAnalysis: (String?) -> Unit,
    onSaveResult: (notes: String, batchName: String) -> Unit,
    onClearScan: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var batchTag by remember { mutableStateOf("") }
    var fieldNotes by remember { mutableStateOf("") }
    var showCameraCapture by remember { mutableStateOf(false) }
    var permissionDeniedNote by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            permissionDeniedNote = null
            fieldNotes = ""
            onAnalyzeUri(context, uri, batchTag.ifBlank { null })
        }
    }

    fun launchCameraCapture() {
        permissionDeniedNote = null
        showCameraCapture = true
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            permissionDeniedNote = "Camera permission is optional; you can still select specimen photos from your gallery."
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header + Engine indicator
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Specimen Classifier",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    val activeModelBadge = if (preferences.engineMode == "custom_cnn") {
                        val modelFile = if (selectedTask == ClassificationTask.SPECIES || selectedTask == ClassificationTask.FULL_SCAN) {
                            preferences.speciesModelFile.ifBlank { selectedTask.kerasModelFile }
                        } else {
                            selectedTask.kerasModelFile
                        }
                        "CNN: $modelFile"
                    } else {
                        "Gemini Vision AI"
                    }
                    Surface(
                        onClick = onOpenSettings,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("classify_engine_badge")
                    ) {
                        Text(
                            text = activeModelBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Text(
                    text = selectedTask.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Task Selector Chips
        item {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ClassificationTask.entries, key = { it.key }) { task ->
                    val selected = task == selectedTask
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTask(task) },
                        label = { Text(task.shortLabel) },
                        modifier = Modifier.testTag("task_chip_${task.key}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        val activeSpec = if (selectedTask == ClassificationTask.FULL_SCAN) {
            kerasSpecs[ClassificationTask.SPECIES]
        } else {
            kerasSpecs[selectedTask]
        }
        if (activeSpec != null) {
            item {
                Surface(
                    onClick = onOpenSettings,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                        .testTag("classify_keras_spec_card")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Keras Model Contract (${activeSpec.assetFileName})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeSpec.architectureSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        activeSpec.mismatchAdvisory?.let { advisory ->
                            Text(
                                text = advisory,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        // Capture Buttons (Zero-permission Photo Picker + Runtime Camera)
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("pick_photo_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Select Photo")
                }

                OutlinedButton(
                    onClick = {
                        val hasPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasPerm) {
                            launchCameraCapture()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("camera_capture_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Camera")
                }
            }
        }

        if (permissionDeniedNote != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = permissionDeniedNote.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Scan State Panel
        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            ) {
                when (scanState) {
                    is ScanUiState.Idle -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_scan),
                                    contentDescription = "Specimen scanner guide illustration",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Text(
                                    text = "Ready for Specimen Analysis",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Frame the butterfly wings, caterpillar instar, or hanging chrysalis in clear natural light. Minimum acceptance threshold: ${(preferences.minConfidence * 100).toInt()}%.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    is ScanUiState.Analyzing -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Image(
                                    bitmap = scanState.bitmap.asImageBitmap(),
                                    contentDescription = "Specimen under analysis",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .clip(RoundedCornerShape(18.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                    Column {
                                        Text(
                                            text = "Running ${scanState.task.displayTitle}...",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Evaluating morphology against 18 Marinduque classes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    is ScanUiState.Error -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (scanState.bitmap != null) {
                                    Image(
                                        bitmap = scanState.bitmap.asImageBitmap(),
                                        contentDescription = "Specimen image",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(170.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Analysis Failed",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Text(
                                    text = scanState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (scanState.bitmap != null) {
                                        Button(
                                            onClick = { onRetryAnalysis(batchTag.ifBlank { null }) },
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Retry")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onClearScan,
                                        modifier = Modifier.height(48.dp)
                                    ) {
                                        Text("Reset")
                                    }
                                }
                            }
                        }
                    }

                    is ScanUiState.ResultReady -> {
                        val rec = scanState.output.record
                        val confPct = String.format(Locale.US, "%.1f%%", rec.confidence * 100.0)
                        val isUnknown = !rec.identified || rec.label == ClassificationTaxonomy.UNKNOWN

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box {
                                    Image(
                                        bitmap = scanState.bitmap.asImageBitmap(),
                                        contentDescription = "Classified specimen",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(210.dp)
                                            .clip(RoundedCornerShape(18.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        color = if (isUnknown) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = confPct,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUnknown) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }

                                // Primary Prediction Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ClassificationTask.fromKey(rec.task).displayTitle.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = ClassificationTaxonomy.shortSpeciesName(rec.label),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Icon(
                                        imageVector = if (isUnknown) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { rec.confidence.toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (isUnknown) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                // Multi-task attribute grid
                                val attributes = listOfNotNull(
                                    rec.speciesLabel?.takeIf { it != "N/A" }?.let { "Species" to ClassificationTaxonomy.shortSpeciesName(it) },
                                    rec.stageLabel?.takeIf { it != "N/A" }?.let { "Stage" to it },
                                    rec.diseaseLabel?.takeIf { it != "N/A" }?.let { "Larval Health" to it },
                                    rec.instarLabel?.takeIf { it != "N/A" }?.let { "Instar" to it },
                                    rec.defectLabel?.takeIf { it != "N/A" }?.let { "Pupae Shell" to it },
                                    rec.sexLabel?.takeIf { it != "N/A" }?.let { "Gender" to it }
                                )

                                if (attributes.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        attributes.forEach { (attrName, attrVal) ->
                                            val alertAttr = attrVal == "Diseased" || attrVal == "Defective"
                                            Surface(
                                                color = if (alertAttr) {
                                                    MaterialTheme.colorScheme.errorContainer
                                                } else {
                                                    MaterialTheme.colorScheme.surfaceVariant
                                                },
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                                    Text(
                                                        text = attrName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = attrVal,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = if (alertAttr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (scanState.output.diagnosticSummary.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.AutoAwesome,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = scanState.output.diagnosticSummary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }

                                // Optional Batch Tag & Notes before saving
                                OutlinedTextField(
                                    value = batchTag,
                                    onValueChange = { batchTag = it.take(80) },
                                    label = { Text("Cohort / Enclosure Tag (optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = fieldNotes,
                                    onValueChange = { fieldNotes = it.take(500) },
                                    label = { Text("Field Observation Notes (optional)") },
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = { onSaveResult(fieldNotes, batchTag) },
                                        enabled = !scanState.isSaving && !scanState.isSaved,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                            .testTag("save_classification_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        if (scanState.isSaving) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text("Saving...")
                                        } else {
                                            Icon(
                                                if (scanState.isSaved) Icons.Filled.CheckCircle else Icons.Filled.BookmarkAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (scanState.isSaved) "Saved to Log" else "Save to Log")
                                        }
                                    }
                                    OutlinedButton(
                                        onClick = onClearScan,
                                        modifier = Modifier.height(52.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("New Scan")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCameraCapture) {
        CameraCaptureDialog(
            taskTitle = selectedTask.displayTitle,
            onDismiss = { showCameraCapture = false },
            onUseGalleryInstead = {
                showCameraCapture = false
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onPhotoCaptured = { uri ->
                showCameraCapture = false
                fieldNotes = ""
                onAnalyzeUri(context, uri, batchTag.ifBlank { null })
            }
        )
    }
}

@Composable
private fun CameraCaptureDialog(
    taskTitle: String,
    onDismiss: () -> Unit,
    onUseGalleryInstead: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraHelper = remember(context) { CameraHelper(context) }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var isCapturing by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(cameraHelper) {
        onDispose {
            cameraHelper.release()
        }
    }

    LaunchedEffect(lensFacing, lifecycleOwner) {
        cameraError = null
        cameraHelper.bindCamera(
            lifecycleOwner = lifecycleOwner,
            previewView = previewView,
            lensFacing = lensFacing,
            flashMode = flashMode,
            onError = { err ->
                cameraError = err.localizedMessage ?: "Unable to open device camera."
            }
        )
    }

    LaunchedEffect(flashMode) {
        cameraHelper.updateFlashMode(flashMode)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (cameraError == null) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(40.dp)
                    )
                    Text(
                        text = "Camera Unavailable",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = cameraError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Button(
                        onClick = onUseGalleryInstead,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("Select Photo Instead")
                    }
                }
            }

            // Specimen Framing Reticle
            if (cameraError == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(260.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(24.dp)
                        )
                )
            }

            // Top Controls Bar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                        .testTag("camerax_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close camera",
                        tint = Color.White
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = taskTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                                else -> ImageCapture.FLASH_MODE_AUTO
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("camerax_flash_button")
                    ) {
                        val flashIcon = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Filled.FlashOff
                            else -> Icons.Filled.FlashAuto
                        }
                        Icon(
                            imageVector = flashIcon,
                            contentDescription = "Toggle flash mode",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .testTag("camerax_switch_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cameraswitch,
                            contentDescription = "Switch camera lens",
                            tint = Color.White
                        )
                    }
                }
            }

            // Bottom Shutter Bar
            if (cameraError == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Center butterfly, larva, or pupa inside frame",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Button(
                        onClick = {
                            if (!isCapturing) {
                                isCapturing = true
                                cameraHelper.capturePhotoToFile(
                                    onImageCaptured = { uri ->
                                        isCapturing = false
                                        onPhotoCaptured(uri)
                                    },
                                    onError = { err ->
                                        isCapturing = false
                                        cameraError = err.localizedMessage ?: "Capture failed."
                                    }
                                )
                            }
                        },
                        enabled = !isCapturing,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(76.dp)
                            .testTag("camerax_shutter_button")
                    ) {
                        if (isCapturing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = "Capture specimen photo",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

