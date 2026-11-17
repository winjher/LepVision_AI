package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BreedingBatch
import com.example.data.model.ClassificationRecord
import com.example.data.model.ClassificationTaxonomy
import com.example.data.model.RiskLevel
import com.example.data.model.UiState
import com.example.data.model.YieldCalculator
import java.util.Locale

@Composable
fun YieldScreen(
    batchesState: UiState<List<BreedingBatch>>,
    classificationsState: UiState<List<ClassificationRecord>>,
    onSaveBatch: (BreedingBatch) -> Unit,
    onDeleteBatch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val batches = (batchesState as? UiState.Success)?.data ?: emptyList()
    val records = (classificationsState as? UiState.Success)?.data ?: emptyList()

    var editingBatch by remember { mutableStateOf<BreedingBatch?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    val totalProjectedAdults = remember(batches) { batches.sumOf { it.predictedAdultCount } }
    val avgYieldPct = remember(batches) {
        if (batches.isEmpty()) 0.0 else batches.map { it.predictedYieldPercent }.average()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Yield Prediction Studio",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Forecast viable adult emergence from larval disease rates, pupae defect inspections, and host-plant health.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Aggregated Summary Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ACTIVE SANCTUARY FORECAST",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = if (batches.isEmpty()) "No cohorts yet" else "${String.format(Locale.US, "%.1f%%", avgYieldPct)} Avg Yield",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalProjectedAdults viable adults projected across ${batches.size} cohort(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Button(
                        onClick = {
                            editingBatch = null
                            showEditor = true
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("new_batch_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New Cohort")
                    }
                }
            }
        }

        when (batchesState) {
            is UiState.Loading -> {
                item {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
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
                            text = batchesState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (batches.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Filled.QueryStats,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Start a Breeding Cohort",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Create a cohort for your butterfly cage or sync healthy/diseased counts directly from your scanned specimen logs.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(batches, key = { it.id }) { batch ->
                        BatchYieldCard(
                            batch = batch,
                            onEdit = {
                                editingBatch = batch
                                showEditor = true
                            },
                            onDelete = { onDeleteBatch(batch.id) },
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        BatchEditorDialog(
            initialBatch = editingBatch,
            classificationRecords = records,
            onDismiss = { showEditor = false },
            onSave = { saved ->
                onSaveBatch(saved)
                showEditor = false
            }
        )
    }
}

@Composable
private fun BatchYieldCard(
    batch: BreedingBatch,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forecast = remember(batch) {
        YieldCalculator.computeYield(
            initialCount = batch.initialCount,
            healthyLarvae = batch.healthyLarvaeCount,
            diseasedLarvae = batch.diseasedLarvaeCount,
            goodPupae = batch.goodPupaeCount,
            defectivePupae = batch.defectivePupaeCount,
            hostPlantHealth = batch.hostPlantHealth
        )
    }

    val riskColor = when (forecast.riskLevel) {
        RiskLevel.OPTIMAL -> MaterialTheme.colorScheme.primary
        RiskLevel.WATCH -> MaterialTheme.colorScheme.secondary
        RiskLevel.CRITICAL -> MaterialTheme.colorScheme.error
    }

    val healthyColor = MaterialTheme.colorScheme.primary
    val diseasedColor = MaterialTheme.colorScheme.error
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.testTag("batch_card_${batch.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = batch.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${ClassificationTaxonomy.shortSpeciesName(batch.targetSpecies)} • Host: ${batch.hostPlantHealth}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = riskColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = forecast.riskLevel.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Predicted Yield Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${String.format(Locale.US, "%.1f", batch.predictedYieldPercent)}%",
                        style = MaterialTheme.typography.displaySmall,
                        color = riskColor
                    )
                    Text(
                        text = "Predicted Emergence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${batch.predictedAdultCount} / ${batch.initialCount}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Est. Viable Adults",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Custom Canvas Health Ratio Bar (Larvae & Pupae)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Larvae (${batch.healthyLarvaeCount} healthy / ${batch.diseasedLarvaeCount} diseased) • Pupae (${batch.goodPupaeCount} good / ${batch.defectivePupaeCount} defective)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    val total = (batch.healthyLarvaeCount + batch.diseasedLarvaeCount + batch.goodPupaeCount + batch.defectivePupaeCount).toFloat()
                    if (total <= 0f) {
                        drawRoundRect(
                            color = trackColor,
                            size = size,
                            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                        )
                    } else {
                        val goodRatio = (batch.healthyLarvaeCount + batch.goodPupaeCount).toFloat() / total
                        val badRatio = 1f - goodRatio
                        val goodW = size.width * goodRatio
                        drawRoundRect(
                            color = trackColor,
                            size = size,
                            cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                        )
                        if (goodW > 0f) {
                            drawRoundRect(
                                color = healthyColor,
                                size = Size(goodW, size.height),
                                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                            )
                        }
                        if (badRatio > 0f && size.width - goodW > 2f) {
                            drawRoundRect(
                                color = diseasedColor,
                                topLeft = Offset(goodW, 0f),
                                size = Size(size.width - goodW, size.height),
                                cornerRadius = CornerRadius(size.height / 2, size.height / 2)
                            )
                        }
                    }
                }
            }

            // Conservation Recommendation
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = forecast.recommendations.first(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit breeding cohort")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete breeding cohort",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BatchEditorDialog(
    initialBatch: BreedingBatch?,
    classificationRecords: List<ClassificationRecord>,
    onDismiss: () -> Unit,
    onSave: (BreedingBatch) -> Unit
) {
    var name by remember { mutableStateOf(initialBatch?.name ?: "Boac Cohort #1") }
    var targetSpecies by remember { mutableStateOf(initialBatch?.targetSpecies ?: "Butterfly-Golden Birdwing") }
    var speciesExpanded by remember { mutableStateOf(false) }

    var initialCountText by remember { mutableStateOf((initialBatch?.initialCount ?: 50L).toString()) }
    var healthyLarvaeText by remember { mutableStateOf((initialBatch?.healthyLarvaeCount ?: 42L).toString()) }
    var diseasedLarvaeText by remember { mutableStateOf((initialBatch?.diseasedLarvaeCount ?: 4L).toString()) }
    var goodPupaeText by remember { mutableStateOf((initialBatch?.goodPupaeCount ?: 36L).toString()) }
    var defectivePupaeText by remember { mutableStateOf((initialBatch?.defectivePupaeCount ?: 2L).toString()) }
    var hostHealth by remember { mutableStateOf(initialBatch?.hostPlantHealth ?: "Optimal") }
    var notes by remember { mutableStateOf(initialBatch?.notes.orEmpty()) }

    val initialCount = initialCountText.toLongOrNull()?.coerceIn(1L, 100_000L) ?: 1L
    val healthyLarvae = healthyLarvaeText.toLongOrNull()?.coerceIn(0L, 100_000L) ?: 0L
    val diseasedLarvae = diseasedLarvaeText.toLongOrNull()?.coerceIn(0L, 100_000L) ?: 0L
    val goodPupae = goodPupaeText.toLongOrNull()?.coerceIn(0L, 100_000L) ?: 0L
    val defectivePupae = defectivePupaeText.toLongOrNull()?.coerceIn(0L, 100_000L) ?: 0L

    val liveForecast = remember(
        initialCount, healthyLarvae, diseasedLarvae, goodPupae, defectivePupae, hostHealth
    ) {
        YieldCalculator.computeYield(
            initialCount, healthyLarvae, diseasedLarvae, goodPupae, defectivePupae, hostHealth
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialBatch == null) "New Breeding Cohort" else "Edit Cohort Forecast",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("Cohort / Enclosure Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_name_input")
                )

                ExposedDropdownMenuBox(
                    expanded = speciesExpanded,
                    onExpandedChange = { speciesExpanded = !speciesExpanded }
                ) {
                    OutlinedTextField(
                        value = ClassificationTaxonomy.shortSpeciesName(targetSpecies),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Target Species") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speciesExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = speciesExpanded,
                        onDismissRequest = { speciesExpanded = false }
                    ) {
                        ClassificationTaxonomy.SPECIES_LIST.forEach { sp ->
                            DropdownMenuItem(
                                text = { Text(ClassificationTaxonomy.shortSpeciesName(sp)) },
                                onClick = {
                                    targetSpecies = sp
                                    speciesExpanded = false
                                }
                            )
                        }
                    }
                }

                // Sync counts from scanned logs helper
                if (classificationRecords.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            val hLarvae = classificationRecords.count { it.diseaseLabel == "Healthy" }.toLong()
                            val dLarvae = classificationRecords.count { it.diseaseLabel == "Diseased" }.toLong()
                            val gPupae = classificationRecords.count { it.defectLabel == "Good" }.toLong()
                            val bPupae = classificationRecords.count { it.defectLabel == "Defective" }.toLong()
                            val totalObs = (hLarvae + dLarvae + gPupae + bPupae).coerceAtLeast(1L)
                            healthyLarvaeText = hLarvae.toString()
                            diseasedLarvaeText = dLarvae.toString()
                            goodPupaeText = gPupae.toString()
                            defectivePupaeText = bPupae.toString()
                            if (initialCount < totalObs) {
                                initialCountText = totalObs.toString()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sync Counts from Scan Logs")
                    }
                }

                OutlinedTextField(
                    value = initialCountText,
                    onValueChange = { initialCountText = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("Initial Cohort Count (Eggs/Larvae)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = healthyLarvaeText,
                        onValueChange = { healthyLarvaeText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Healthy Larvae") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = diseasedLarvaeText,
                        onValueChange = { diseasedLarvaeText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Diseased Larvae") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = goodPupaeText,
                        onValueChange = { goodPupaeText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Good Pupae") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = defectivePupaeText,
                        onValueChange = { defectivePupaeText = it.filter { ch -> ch.isDigit() }.take(6) },
                        label = { Text("Defective Pupae") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Host Plant Health", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Optimal", "Moderate", "Stressed").forEach { option ->
                        FilterChip(
                            selected = hostHealth == option,
                            onClick = { hostHealth = option },
                            label = { Text(option) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Notes (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Prediction Preview
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Predicted Yield: ${String.format(Locale.US, "%.1f%%", liveForecast.predictedYieldPercent)} (${liveForecast.predictedAdultCount} adults)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = liveForecast.riskLevel.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val saved = BreedingBatch(
                        id = initialBatch?.id ?: "",
                        userId = initialBatch?.userId ?: "",
                        name = name.trim().ifBlank { "Marinduque Cohort" },
                        targetSpecies = targetSpecies,
                        initialCount = initialCount,
                        healthyLarvaeCount = healthyLarvae,
                        diseasedLarvaeCount = diseasedLarvae,
                        goodPupaeCount = goodPupae,
                        defectivePupaeCount = defectivePupae,
                        predictedYieldPercent = liveForecast.predictedYieldPercent,
                        predictedAdultCount = liveForecast.predictedAdultCount,
                        hostPlantHealth = hostHealth,
                        notes = notes.trim().takeIf { it.isNotBlank() }
                    )
                    onSave(saved)
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("save_batch_button")
            ) {
                Text("Save Cohort")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
