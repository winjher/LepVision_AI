package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.BreedingBatch
import com.example.data.model.ClassificationRecord
import com.example.data.model.ClassificationTask
import com.example.data.model.ClassificationTaxonomy
import com.example.data.model.KerasModelSpec
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.ui.viewmodel.LepVisionViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    userEmail: String?,
    classificationsState: UiState<List<ClassificationRecord>>,
    batchesState: UiState<List<BreedingBatch>>,
    preferences: UserPreferences,
    kerasSpecs: Map<ClassificationTask, KerasModelSpec> = emptyMap(),
    onNavigateToClassify: (ClassificationTask) -> Unit,
    onNavigateToYield: () -> Unit,
    onOpenModelSettings: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val records = (classificationsState as? UiState.Success)?.data ?: emptyList()
    val batches = (batchesState as? UiState.Success)?.data ?: emptyList()

    val speciesFound = remember(records) { LepVisionViewModel.computeSpeciesFound(records) }
    val datasetCounts = remember(records) { LepVisionViewModel.computeDatasetCounts(records) }
    val avgYield = remember(batches) {
        if (batches.isEmpty()) null else batches.map { it.predictedYieldPercent }.average()
    }
    val avgConfidence = remember(records) {
        if (records.isEmpty()) 0.0 else records.map { it.confidence }.average()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Botanical Command Hero
        item {
            Box(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_marinduque_hero),
                    contentDescription = "Marinduque butterfly sanctuary banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.25f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "MARINDUQUE FIELD MONITOR",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    )
                    Text(
                        text = "LepVision Observatory",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = "Researcher: $userEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // KPI Metrics Strip
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiMetricCard(
                    title = "Species Found",
                    value = "${speciesFound.size}/18",
                    subtitle = "Catalog coverage",
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Specimens",
                    value = "${records.size}",
                    subtitle = if (records.isEmpty()) "Ready to scan" else "${(avgConfidence * 100).toInt()}% avg conf",
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Yield Forecast",
                    value = avgYield?.let { String.format(Locale.US, "%.0f%%", it) } ?: "--",
                    subtitle = "${batches.size} cohorts",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Quick Actions Row
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onNavigateToClassify(ClassificationTask.FULL_SCAN) },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("dashboard_scan_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Scan Specimen", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onNavigateToYield,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("dashboard_yield_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Predict Yield", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onExportCsv,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("dashboard_export_button"),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = "Export dataset manifest CSV")
                }
            }
        }

        // Google Drive & Keras Model Registry Card
        item {
            DriveModelRegistryCard(
                preferences = preferences,
                kerasSpecs = kerasSpecs,
                onOpenFolder = { folderId ->
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://drive.google.com/drive/folders/$folderId")
                    )
                    runCatching { context.startActivity(intent) }.onFailure {
                        Toast.makeText(context, "Unable to open Google Drive link.", Toast.LENGTH_SHORT).show()
                    }
                },
                onConfigureModel = onOpenModelSettings,
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
            )
        }

        // Loading / Error banner if Firestore stream is initializing or failing
        when (classificationsState) {
            is UiState.Loading -> {
                item {
                    Row(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Syncing conservatory records...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = classificationsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            is UiState.Success -> Unit
        }

        // Marinduque Species Found Section
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (speciesFound.isNotEmpty()) "Marinduque Species Found" else "Endemic Marinduque Catalog",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "${speciesFound.size}/18 logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (speciesFound.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(speciesFound, key = { it.officialLabel }) { item ->
                            val info = ClassificationTaxonomy.speciesInfoFor(item.officialLabel)
                            Card(
                                modifier = Modifier.width(220.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp)
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
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = "${item.count}x seen",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "${(item.bestConfidence * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = item.commonName,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.scientificName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (info != null) {
                                        Text(
                                            text = "Host: ${info.hostPlant}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Show flagship Marinduque species reference cards so new users see the conservation target list
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(ClassificationTaxonomy.SPECIES_CATALOG.take(6), key = { it.officialLabel }) { info ->
                            Card(
                                modifier = Modifier
                                    .width(230.dp)
                                    .clickable { onNavigateToClassify(ClassificationTask.SPECIES) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.Eco,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = info.commonName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = info.scientificName,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Host: ${info.hostPlant}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = info.conservationNote,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6-Task Dataset Status Matrix (Mirrors Classification.gs clsDataset_)
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "6-Model Classification Dataset",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Tap any diagnostic module to classify a specimen photo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(ClassificationTask.singleTasks, key = { it.key }) { task ->
            val labelCounts = datasetCounts[task].orEmpty()
            val totalForTask = labelCounts.values.sum()
            val populatedClasses = labelCounts.count { it.value > 0 }
            val totalClasses = labelCounts.size.coerceAtLeast(1)

            Card(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .clickable { onNavigateToClassify(task) }
                    .testTag("dataset_task_card_${task.key}"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.displayTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            val modelName = if (task == ClassificationTask.SPECIES) {
                                preferences.speciesModelFile.ifBlank { task.kerasModelFile }
                            } else {
                                task.kerasModelFile
                            }
                            val specTag = kerasSpecs[task]?.let {
                                " • ${it.inputWidth}×${it.inputHeight} (${it.outputUnits} ${if (it.fromLogits) "logits" else it.outputActivation})"
                            }.orEmpty()
                            Text(
                                text = "$modelName$specTag • $populatedClasses/$totalClasses classes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = if (totalForTask > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (totalForTask > 0) {
                                    Icon(
                                        Icons.Filled.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = "$totalForTask scans",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { populatedClasses.toFloat() / totalClasses.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    val activeCounts = labelCounts.entries.filter { it.value > 0 }
                    if (activeCounts.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(activeCounts) { (lbl, cnt) ->
                                AssistChip(
                                    onClick = { onNavigateToClassify(task) },
                                    label = {
                                        Text(
                                            text = "${ClassificationTaxonomy.shortSpeciesName(lbl)}: $cnt",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveModelRegistryCard(
    preferences: UserPreferences,
    kerasSpecs: Map<ClassificationTask, KerasModelSpec>,
    onOpenFolder: (String) -> Unit,
    onConfigureModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCustomCnn = preferences.engineMode == "custom_cnn"
    val speciesModel = preferences.speciesModelFile.ifBlank { "butterfly_species.keras" }
    val drivePath = preferences.driveDirectoryPath.ifBlank { "Drive/Butterfly Dataset/" }
    val folderId = preferences.driveFolderId.ifBlank { "1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN" }
    val speciesSpec = kerasSpecs[ClassificationTask.SPECIES]
    val stageSpec = kerasSpecs[ClassificationTask.STAGE]

    Card(
        modifier = modifier.testTag("drive_model_registry_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudQueue,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Drive & Keras Model Registry",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = if (isCustomCnn) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isCustomCnn) "CNN Server (api.py)" else "Gemini Vision AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCustomCnn) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = speciesModel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = drivePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            if (speciesSpec != null || stageSpec != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        speciesSpec?.let { spec ->
                            Text(
                                text = "Species CNN: ${spec.architectureSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        stageSpec?.let { spec ->
                            Text(
                                text = "Stages CNN: ${spec.architectureSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            speciesSpec?.mismatchAdvisory?.let { advisory ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = advisory,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Training Dataset Links (18 Species & Life Stages)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { onOpenFolder("1CGkRwxh7XSNFlM9XcSZLQ--FLaS4MLSU") },
                    label = { Text("18 Species Folder", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_species_drive_chip")
                )
                AssistChip(
                    onClick = { onOpenFolder("1Pgv0D6D24JEgkfRaJSWETdYMsZlqESet") },
                    label = { Text("Life Stages Folder", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dashboard_stages_drive_chip")
                )
            }

            Text(
                text = if (isCustomCnn) {
                    if (preferences.customCnnUrl.isBlank()) {
                        "Custom CNN active — configure your api.py server URL to execute $speciesModel."
                    } else {
                        "Endpoint: ${preferences.customCnnUrl}"
                    }
                } else {
                    "Root Drive ID: $folderId • Gemini Vision AI is active on-device; switch to Custom CNN in settings to route scans to your Drive .keras server (api.py)."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onOpenFolder(folderId) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_open_drive_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Open Drive", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(
                    onClick = onConfigureModel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_configure_model_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Configure Model", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
