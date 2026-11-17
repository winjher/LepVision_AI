package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BreedingBatch
import com.example.data.model.ClassificationRecord
import com.example.data.model.ClassificationTask
import com.example.data.model.ClassificationTaxonomy
import com.example.data.model.KerasModelSpec
import com.example.data.model.SpeciesDiscoverySummary
import com.example.data.model.UiState
import com.example.data.model.UserPreferences
import com.example.data.repository.ClassificationAiRepository
import com.example.data.repository.ClassificationOutput
import com.example.data.repository.ConservationRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Analyzing(val bitmap: Bitmap, val task: ClassificationTask) : ScanUiState
    data class ResultReady(
        val bitmap: Bitmap,
        val output: ClassificationOutput,
        val isSaving: Boolean = false,
        val isSaved: Boolean = false
    ) : ScanUiState
    data class Error(val bitmap: Bitmap?, val message: String) : ScanUiState
}

sealed interface ServerTestUiState {
    data object Idle : ServerTestUiState
    data object Testing : ServerTestUiState
    data class Success(val message: String) : ServerTestUiState
    data class Error(val message: String) : ServerTestUiState
}

class LepVisionViewModel(
    private val conservationRepository: ConservationRepository,
    private val aiRepository: ClassificationAiRepository,
    val currentUserId: String,
    val kerasSpecs: Map<ClassificationTask, KerasModelSpec> = emptyMap()
) : ViewModel() {

    fun specForTask(task: ClassificationTask): KerasModelSpec? {
        return if (task == ClassificationTask.FULL_SCAN) {
            kerasSpecs[ClassificationTask.SPECIES]
        } else {
            kerasSpecs[task]
        }
    }

    val classificationsState: StateFlow<UiState<List<ClassificationRecord>>> =
        conservationRepository.observeClassifications(currentUserId)
            .map<List<ClassificationRecord>, UiState<List<ClassificationRecord>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing classifications", root)
                emit(UiState.Error(root.message ?: "Failed to load classification logs."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val batchesState: StateFlow<UiState<List<BreedingBatch>>> =
        conservationRepository.observeBatches(currentUserId)
            .map<List<BreedingBatch>, UiState<List<BreedingBatch>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing breeding batches", root)
                emit(UiState.Error(root.message ?: "Failed to load breeding batches."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val preferencesState: StateFlow<UserPreferences> =
        conservationRepository.observePreferences(currentUserId)
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                Log.w(TAG, "Error observing preferences", firestoreError ?: error)
                emit(UserPreferences(userId = currentUserId))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UserPreferences(userId = currentUserId)
            )

    private val _selectedTask = MutableStateFlow(ClassificationTask.FULL_SCAN)
    val selectedTask: StateFlow<ClassificationTask> = _selectedTask.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private val _serverTestState = MutableStateFlow<ServerTestUiState>(ServerTestUiState.Idle)
    val serverTestState: StateFlow<ServerTestUiState> = _serverTestState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun selectTask(task: ClassificationTask) {
        _selectedTask.value = task
    }

    fun analyzeImageUri(context: Context, uri: Uri, batchName: String?) {
        val task = _selectedTask.value
        val prefs = preferencesState.value
        viewModelScope.launch {
            var loadedBitmap: Bitmap? = null
            try {
                loadedBitmap = aiRepository.loadDownscaledBitmap(context, uri)
                _scanState.value = ScanUiState.Analyzing(loadedBitmap, task)
                val result = aiRepository.classifySpecimen(
                    bitmap = loadedBitmap,
                    task = task,
                    minConfidence = prefs.minConfidence,
                    engineMode = prefs.engineMode,
                    customCnnUrl = prefs.customCnnUrl,
                    driveFolderId = prefs.driveFolderId,
                    speciesModelFile = prefs.speciesModelFile,
                    kerasSpec = specForTask(task),
                    batchName = batchName
                )
                _scanState.value = ScanUiState.ResultReady(bitmap = loadedBitmap, output = result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Specimen analysis failed", e)
                _scanState.value = ScanUiState.Error(
                    bitmap = loadedBitmap,
                    message = e.localizedMessage ?: "Failed to analyze specimen image."
                )
            }
        }
    }

    fun retryLastAnalysis(batchName: String?) {
        val current = _scanState.value
        val bitmap = when (current) {
            is ScanUiState.Error -> current.bitmap
            is ScanUiState.ResultReady -> current.bitmap
            else -> null
        } ?: return

        val task = _selectedTask.value
        val prefs = preferencesState.value
        viewModelScope.launch {
            _scanState.value = ScanUiState.Analyzing(bitmap, task)
            try {
                val result = aiRepository.classifySpecimen(
                    bitmap = bitmap,
                    task = task,
                    minConfidence = prefs.minConfidence,
                    engineMode = prefs.engineMode,
                    customCnnUrl = prefs.customCnnUrl,
                    driveFolderId = prefs.driveFolderId,
                    speciesModelFile = prefs.speciesModelFile,
                    kerasSpec = specForTask(task),
                    batchName = batchName
                )
                _scanState.value = ScanUiState.ResultReady(bitmap = bitmap, output = result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Retry specimen analysis failed", e)
                _scanState.value = ScanUiState.Error(
                    bitmap = bitmap,
                    message = e.localizedMessage ?: "Analysis failed."
                )
            }
        }
    }

    fun saveCurrentScanResult(notes: String, batchName: String) {
        val current = _scanState.value as? ScanUiState.ResultReady ?: return
        if (current.isSaving || current.isSaved) return

        viewModelScope.launch {
            _scanState.value = current.copy(isSaving = true)
            val updatedRecord = current.output.record.copy(
                notes = notes.trim().takeIf { it.isNotBlank() },
                batchName = batchName.trim().takeIf { it.isNotBlank() }
            )
            val result = conservationRepository.saveClassification(currentUserId, updatedRecord)
            if (result.isSuccess) {
                _scanState.value = current.copy(isSaving = false, isSaved = true)
                _events.emit("Saved classification to conservation log.")
            } else {
                _scanState.value = current.copy(isSaving = false)
                _events.emit(
                    result.exceptionOrNull()?.localizedMessage ?: "Failed to save record."
                )
            }
        }
    }

    fun clearScan() {
        _scanState.value = ScanUiState.Idle
    }

    fun updateRecordNotes(recordId: String, notes: String, batchName: String) {
        viewModelScope.launch {
            val result = conservationRepository.updateClassificationNotes(
                userId = currentUserId,
                recordId = recordId,
                notes = notes,
                batchName = batchName
            )
            if (result.isSuccess) {
                _events.emit("Specimen record updated.")
            } else {
                _events.emit(result.exceptionOrNull()?.localizedMessage ?: "Failed to update record.")
            }
        }
    }

    fun deleteRecord(recordId: String) {
        viewModelScope.launch {
            val result = conservationRepository.deleteClassification(currentUserId, recordId)
            if (result.isSuccess) {
                _events.emit("Classification record deleted.")
            } else {
                _events.emit(result.exceptionOrNull()?.localizedMessage ?: "Failed to delete record.")
            }
        }
    }

    fun saveBatch(batch: BreedingBatch) {
        viewModelScope.launch {
            val result = conservationRepository.saveBatch(currentUserId, batch)
            if (result.isSuccess) {
                _events.emit("Breeding cohort saved.")
            } else {
                _events.emit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save breeding cohort.")
            }
        }
    }

    fun deleteBatch(batchId: String) {
        viewModelScope.launch {
            val result = conservationRepository.deleteBatch(currentUserId, batchId)
            if (result.isSuccess) {
                _events.emit("Breeding cohort removed.")
            } else {
                _events.emit(result.exceptionOrNull()?.localizedMessage ?: "Failed to remove cohort.")
            }
        }
    }

    fun clearServerTestState() {
        _serverTestState.value = ServerTestUiState.Idle
    }

    fun testCnnServer(url: String) {
        viewModelScope.launch {
            _serverTestState.value = ServerTestUiState.Testing
            val result = aiRepository.testCustomCnnConnection(url)
            _serverTestState.value = result.fold(
                onSuccess = { ServerTestUiState.Success(it) },
                onFailure = { ServerTestUiState.Error(it.localizedMessage ?: "Connection failed.") }
            )
        }
    }

    fun savePreferences(
        engineMode: String,
        customCnnUrl: String,
        driveFolderId: String,
        driveDirectoryPath: String,
        speciesModelFile: String,
        minConfidence: Double
    ) {
        viewModelScope.launch {
            val prefs = UserPreferences(
                userId = currentUserId,
                engineMode = engineMode,
                customCnnUrl = customCnnUrl,
                driveFolderId = driveFolderId,
                driveDirectoryPath = driveDirectoryPath,
                speciesModelFile = speciesModelFile,
                minConfidence = minConfidence
            )
            val result = conservationRepository.savePreferences(currentUserId, prefs)
            if (result.isSuccess) {
                _events.emit("Model & Drive settings saved.")
            } else {
                _events.emit(result.exceptionOrNull()?.localizedMessage ?: "Failed to save settings.")
            }
        }
    }

    suspend fun exportManifestCsvUri(context: Context): Uri? {
        val state = classificationsState.value
        val records = (state as? UiState.Success)?.data ?: emptyList()
        if (records.isEmpty()) {
            _events.emit("Log at least one specimen before exporting a manifest.")
            return null
        }
        return withContext(Dispatchers.IO) {
            val csv = conservationRepository.buildManifestCsv(records)
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(exportsDir, "lepvision_dataset_manifest.csv")
            outFile.writeText(csv)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        }
    }

    companion object {
        private const val TAG = "LepVisionVM"

        fun computeSpeciesFound(records: List<ClassificationRecord>): List<SpeciesDiscoverySummary> {
            val map = mutableMapOf<String, SpeciesDiscoverySummary>()
            records.forEach { rec ->
                val sp = when {
                    !rec.speciesLabel.isNullOrBlank() && rec.speciesLabel != ClassificationTaxonomy.UNKNOWN -> rec.speciesLabel
                    rec.task == ClassificationTask.SPECIES.key && rec.label != ClassificationTaxonomy.UNKNOWN -> rec.label
                    else -> null
                } ?: return@forEach

                val info = ClassificationTaxonomy.speciesInfoFor(sp)
                val ts = rec.createdAt?.toDate()?.time ?: 0L
                val existing = map[sp]
                if (existing == null) {
                    map[sp] = SpeciesDiscoverySummary(
                        officialLabel = sp,
                        commonName = info?.commonName ?: ClassificationTaxonomy.shortSpeciesName(sp),
                        scientificName = info?.scientificName ?: "Marinduque Lepidoptera",
                        count = 1,
                        bestConfidence = rec.confidence,
                        lastSeenMillis = ts
                    )
                } else {
                    map[sp] = existing.copy(
                        count = existing.count + 1,
                        bestConfidence = maxOf(existing.bestConfidence, rec.confidence),
                        lastSeenMillis = maxOf(existing.lastSeenMillis, ts)
                    )
                }
            }
            return map.values.sortedByDescending { it.count }
        }

        fun computeDatasetCounts(records: List<ClassificationRecord>): Map<ClassificationTask, Map<String, Int>> {
            val result = mutableMapOf<ClassificationTask, MutableMap<String, Int>>()
            ClassificationTask.singleTasks.forEach { task ->
                val counts = mutableMapOf<String, Int>()
                ClassificationTaxonomy.classesForTask(task).forEach { label ->
                    counts[label] = 0
                }
                result[task] = counts
            }

            records.forEach { rec ->
                // Count direct task records or multi-task full scans
                val sp = rec.speciesLabel
                if (!sp.isNullOrBlank() && sp != ClassificationTaxonomy.UNKNOWN && sp != "N/A") {
                    result[ClassificationTask.SPECIES]?.let { m -> m[sp] = (m[sp] ?: 0) + 1 }
                }
                val st = rec.stageLabel
                if (!st.isNullOrBlank() && st != ClassificationTaxonomy.UNKNOWN && st != "N/A") {
                    result[ClassificationTask.STAGE]?.let { m -> m[st] = (m[st] ?: 0) + 1 }
                }
                val dis = rec.diseaseLabel
                if (!dis.isNullOrBlank() && dis != ClassificationTaxonomy.UNKNOWN && dis != "N/A") {
                    result[ClassificationTask.DISEASE]?.let { m -> m[dis] = (m[dis] ?: 0) + 1 }
                }
                val ins = rec.instarLabel
                if (!ins.isNullOrBlank() && ins != ClassificationTaxonomy.UNKNOWN && ins != "N/A") {
                    result[ClassificationTask.INSTAR]?.let { m -> m[ins] = (m[ins] ?: 0) + 1 }
                }
                val def = rec.defectLabel
                if (!def.isNullOrBlank() && def != ClassificationTaxonomy.UNKNOWN && def != "N/A") {
                    result[ClassificationTask.DEFECT]?.let { m -> m[def] = (m[def] ?: 0) + 1 }
                }
                val sex = rec.sexLabel
                if (!sex.isNullOrBlank() && sex != ClassificationTaxonomy.UNKNOWN && sex != "N/A") {
                    result[ClassificationTask.SEX]?.let { m -> m[sex] = (m[sex] ?: 0) + 1 }
                }
            }
            return result
        }
    }
}
