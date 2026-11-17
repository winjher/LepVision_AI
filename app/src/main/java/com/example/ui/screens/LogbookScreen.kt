package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.data.model.ClassificationRecord
import com.example.data.model.ClassificationTask
import com.example.data.model.ClassificationTaxonomy
import com.example.data.model.KerasArchitectureParser
import com.example.data.model.KerasModelSpec
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.firebase.signOut
import com.example.ui.viewmodel.ServerTestUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogbookScreen(
    classificationsState: UiState<List<ClassificationRecord>>,
    onUpdateRecordNotes: (recordId: String, notes: String, batchName: String) -> Unit,
    onDeleteRecord: (recordId: String) -> Unit,
    onOpenSettings: () -> Unit,
    onExportCsv: () -> Unit,
    modifier: Modifier = Modifier
) {
    val records = (classificationsState as? UiState.Success)?.data ?: emptyList()

    var searchQuery by remember { mutableStateOf("") }
    var filterTaskKey by remember { mutableStateOf("all") }
    var editingRecord by remember { mutableStateOf<ClassificationRecord?>(null) }

    val filteredRecords = remember(records, searchQuery, filterTaskKey) {
        records.filter { rec ->
            val matchesTask = filterTaskKey == "all" || rec.task == filterTaskKey
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                rec.label.lowercase().contains(q) ||
                rec.speciesLabel.orEmpty().lowercase().contains(q) ||
                rec.batchName.orEmpty().lowercase().contains(q) ||
                rec.notes.orEmpty().lowercase().contains(q)
            matchesTask && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header + Export + Settings actions
        item {
            Row(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Field Logbook",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${records.size} classified specimens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onExportCsv,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("export_csv_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = "Export CSV manifest")
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("open_settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Engine and account settings")
                    }
                }
            }
        }

        // Search input
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search species, batch, or notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .testTag("logbook_search_input")
            )
        }

        // Task Filter Chips
        item {
            LazyRow(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterTaskKey == "all",
                        onClick = { filterTaskKey = "all" },
                        label = { Text("All") }
                    )
                }
                items(ClassificationTask.entries, key = { it.key }) { task ->
                    FilterChip(
                        selected = filterTaskKey == task.key,
                        onClick = { filterTaskKey = task.key },
                        label = { Text(task.shortLabel) }
                    )
                }
            }
        }

        when (classificationsState) {
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
                            text = classificationsState.message,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (filteredRecords.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (records.isEmpty()) "No Specimen Scans Saved Yet" else "No Matching Records",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (records.isEmpty()) {
                                        "Head to the Classify tab to analyze a butterfly, caterpillar, or chrysalis photo."
                                    } else {
                                        "Try clearing the search query or switching the task filter."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredRecords, key = { it.id }) { record ->
                        ClassificationLogCard(
                            record = record,
                            onEdit = { editingRecord = record },
                            onDelete = { onDeleteRecord(record.id) },
                            modifier = Modifier
                                .widthIn(max = 720.dp)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    editingRecord?.let { rec ->
        EditRecordNotesDialog(
            record = rec,
            onDismiss = { editingRecord = null },
            onSave = { notes, batchName ->
                onUpdateRecordNotes(rec.id, notes, batchName)
                editingRecord = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClassificationLogCard(
    record: ClassificationRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    val dateStr = dateFormatter.format(record.createdAt?.toDate() ?: Date())
    val taskInfo = ClassificationTask.fromKey(record.task)

    Card(
        modifier = modifier.testTag("log_card_${record.id}"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = taskInfo.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(record.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.identified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = ClassificationTaxonomy.shortSpeciesName(record.label),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val tags = listOfNotNull(
                record.stageLabel?.takeIf { it != "N/A" }?.let { "Stage: $it" },
                record.diseaseLabel?.takeIf { it != "N/A" }?.let { "Health: $it" },
                record.instarLabel?.takeIf { it != "N/A" },
                record.defectLabel?.takeIf { it != "N/A" }?.let { "Pupae: $it" },
                record.sexLabel?.takeIf { it != "N/A" }?.let { "Sex: $it" },
                record.batchName?.takeIf { it.isNotBlank() }?.let { "Cohort: $it" }
            )
            if (tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tags.forEach { t ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = t,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (!record.summary.isNullOrBlank()) {
                Text(
                    text = record.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!record.notes.isNullOrBlank()) {
                Text(
                    text = "Notes: ${record.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit notes")
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Delete record",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EditRecordNotesDialog(
    record: ClassificationRecord,
    onDismiss: () -> Unit,
    onSave: (notes: String, batchName: String) -> Unit
) {
    var notes by remember { mutableStateOf(record.notes.orEmpty()) }
    var batchName by remember { mutableStateOf(record.batchName.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Specimen Notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = batchName,
                    onValueChange = { batchName = it.take(80) },
                    label = { Text("Cohort / Enclosure Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(500) },
                    label = { Text("Field Observation Notes") },
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(notes, batchName) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsAndAccountDialog(
    userEmail: String?,
    preferences: UserPreferences,
    kerasSpecs: Map<ClassificationTask, KerasModelSpec> = emptyMap(),
    serverTestState: ServerTestUiState,
    onTestCnnServer: (String) -> Unit,
    onClearServerTestState: () -> Unit,
    onDismiss: () -> Unit,
    onSavePreferences: (
        engineMode: String,
        customCnnUrl: String,
        driveFolderId: String,
        driveDirectoryPath: String,
        speciesModelFile: String,
        minConfidence: Double
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var engineMode by remember(preferences) { mutableStateOf(preferences.engineMode) }
    var customUrl by remember(preferences) { mutableStateOf(preferences.customCnnUrl) }
    var driveFolderId by remember(preferences) { mutableStateOf(preferences.driveFolderId) }
    var driveDirectoryPath by remember(preferences) { mutableStateOf(preferences.driveDirectoryPath) }
    var speciesModelFile by remember(preferences) { mutableStateOf(preferences.speciesModelFile) }
    var minConf by remember(preferences) { mutableDoubleStateOf(preferences.minConfidence) }

    AlertDialog(
        onDismissRequest = {
            onClearServerTestState()
            onDismiss()
        },
        title = { Text("Model, Drive & Account Settings") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Classification Engine",
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = engineMode == "gemini_vision",
                        onClick = { engineMode = "gemini_vision" },
                        label = { Text("Gemini Vision AI") },
                        modifier = Modifier.testTag("engine_chip_gemini")
                    )
                    FilterChip(
                        selected = engineMode == "custom_cnn",
                        onClick = { engineMode = "custom_cnn" },
                        label = { Text("Custom CNN (api.py)") },
                        modifier = Modifier.testTag("engine_chip_custom_cnn")
                    )
                }

                if (engineMode == "custom_cnn") {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = {
                            customUrl = it.take(300)
                            onClearServerTestState()
                        },
                        label = { Text("CNN Server Base URL (api.py)") },
                        placeholder = { Text("https://your-cnn-server.example.com") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_cnn_url_input")
                    )

                    OutlinedButton(
                        onClick = { onTestCnnServer(customUrl) },
                        enabled = serverTestState !is ServerTestUiState.Testing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_test_server_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (serverTestState is ServerTestUiState.Testing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Testing CNN Server...")
                        } else {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Test CNN Server Connection")
                        }
                    }

                    when (serverTestState) {
                        is ServerTestUiState.Success -> {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = serverTestState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        is ServerTestUiState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = serverTestState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }
                }

                // Google Drive Butterfly Dataset & Keras Model Registry
                Text(
                    text = "Google Drive & Keras Model Registry",
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedTextField(
                    value = driveDirectoryPath,
                    onValueChange = { driveDirectoryPath = it.take(200) },
                    label = { Text("Drive Directory Path") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_drive_path_input")
                )
                OutlinedTextField(
                    value = driveFolderId,
                    onValueChange = { driveFolderId = it.take(128) },
                    label = { Text("Google Drive Folder ID") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_drive_id_input")
                )
                OutlinedTextField(
                    value = speciesModelFile,
                    onValueChange = { speciesModelFile = it.take(100) },
                    label = { Text("Species Keras Model") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_species_model_input")
                )

                Text(
                    text = "6-Task Keras Models in Drive",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ClassificationTask.singleTasks.forEach { t ->
                        val modelLabel = if (t == ClassificationTask.SPECIES) {
                            speciesModelFile.ifBlank { t.kerasModelFile }
                        } else {
                            t.kerasModelFile
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = modelLabel,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val safeId = driveFolderId.trim().ifBlank { "1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN" }
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://drive.google.com/drive/folders/$safeId")
                        )
                        runCatching { context.startActivity(intent) }.onFailure {
                            Toast.makeText(context, "Unable to open Google Drive folder.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("settings_open_drive_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open Butterfly Dataset in Drive")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://drive.google.com/drive/folders/${KerasArchitectureParser.SPECIES_DRIVE_FOLDER_ID}")
                            )
                            runCatching { context.startActivity(intent) }.onFailure {
                                Toast.makeText(context, "Unable to open 18 Species folder.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_open_species_drive_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("18 Species", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://drive.google.com/drive/folders/${KerasArchitectureParser.STAGES_DRIVE_FOLDER_ID}")
                            )
                            runCatching { context.startActivity(intent) }.onFailure {
                                Toast.makeText(context, "Unable to open Life Stages folder.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("settings_open_stages_drive_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Life Stages", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                if (kerasSpecs.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Loaded Keras Architectures",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            kerasSpecs[ClassificationTask.SPECIES]?.let { spec ->
                                Text(
                                    text = "Species (${spec.assetFileName}): ${spec.architectureSummary}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                spec.mismatchAdvisory?.let { msg ->
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            kerasSpecs[ClassificationTask.STAGE]?.let { spec ->
                                Text(
                                    text = "Stages (${spec.assetFileName}): ${spec.architectureSummary}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ".keras archives run through your external Python api.py server (POST /api/classify-json), or convert to .tflite for offline inference. Gemini Vision AI runs built-in without any server.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "Min Acceptance Confidence: ${(minConf * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Slider(
                    value = minConf.toFloat(),
                    onValueChange = { minConf = it.toDouble() },
                    valueRange = 0.30f..0.90f
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Signed in as ${userEmail ?: "Google Researcher"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(
                            onClick = {
                                onClearServerTestState()
                                onDismiss()
                                signOut(credentialManager, scope)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onClearServerTestState()
                    onSavePreferences(
                        engineMode,
                        customUrl,
                        driveFolderId,
                        driveDirectoryPath,
                        speciesModelFile,
                        minConf
                    )
                },
                modifier = Modifier.testTag("save_settings_button")
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClearServerTestState()
                    onDismiss()
                }
            ) {
                Text("Close")
            }
        }
    )
}
