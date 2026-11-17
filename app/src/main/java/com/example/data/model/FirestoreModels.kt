package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class ClassificationRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val task: String = ClassificationTask.FULL_SCAN.key,
    val label: String = ClassificationTaxonomy.UNKNOWN,
    val confidence: Double = 0.0,
    val identified: Boolean = false,
    val speciesLabel: String? = null,
    val stageLabel: String? = null,
    val diseaseLabel: String? = null,
    val instarLabel: String? = null,
    val defectLabel: String? = null,
    val sexLabel: String? = null,
    val notes: String? = null,
    val summary: String? = null,
    val engineMode: String = "gemini_vision",
    val batchName: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "task" to task.take(32),
            "label" to label.ifBlank { ClassificationTaxonomy.UNKNOWN }.take(100),
            "confidence" to confidence.coerceIn(0.0, 1.0),
            "identified" to identified,
            "speciesLabel" to speciesLabel?.takeIf { it.isNotBlank() }?.take(100),
            "stageLabel" to stageLabel?.takeIf { it.isNotBlank() }?.take(50),
            "diseaseLabel" to diseaseLabel?.takeIf { it.isNotBlank() }?.take(50),
            "instarLabel" to instarLabel?.takeIf { it.isNotBlank() }?.take(50),
            "defectLabel" to defectLabel?.takeIf { it.isNotBlank() }?.take(50),
            "sexLabel" to sexLabel?.takeIf { it.isNotBlank() }?.take(50),
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(500),
            "summary" to summary?.takeIf { it.isNotBlank() }?.take(800),
            "engineMode" to engineMode.ifBlank { "gemini_vision" }.take(32),
            "batchName" to batchName?.takeIf { it.isNotBlank() }?.take(80),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class BreedingBatch(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val targetSpecies: String = "Butterfly-Golden Birdwing",
    val initialCount: Long = 50L,
    val healthyLarvaeCount: Long = 40L,
    val diseasedLarvaeCount: Long = 5L,
    val goodPupaeCount: Long = 35L,
    val defectivePupaeCount: Long = 3L,
    val predictedYieldPercent: Double = 70.0,
    val predictedAdultCount: Long = 35L,
    val hostPlantHealth: String = "Optimal",
    val notes: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "userId" to uid,
            "name" to name.trim().take(80),
            "targetSpecies" to targetSpecies.trim().take(100),
            "initialCount" to initialCount.coerceIn(1L, 100_000L),
            "healthyLarvaeCount" to healthyLarvaeCount.coerceIn(0L, 100_000L),
            "diseasedLarvaeCount" to diseasedLarvaeCount.coerceIn(0L, 100_000L),
            "goodPupaeCount" to goodPupaeCount.coerceIn(0L, 100_000L),
            "defectivePupaeCount" to defectivePupaeCount.coerceIn(0L, 100_000L),
            "predictedYieldPercent" to predictedYieldPercent.coerceIn(0.0, 100.0),
            "predictedAdultCount" to predictedAdultCount.coerceIn(0L, 100_000L),
            "hostPlantHealth" to hostPlantHealth.takeIf { it in listOf("Optimal", "Moderate", "Stressed") }.orEmpty().ifBlank { "Optimal" },
            "notes" to notes?.takeIf { it.isNotBlank() }?.take(500),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }
}

@Keep
@IgnoreExtraProperties
data class UserPreferences(
    @DocumentId val id: String = "preferences",
    val userId: String = "",
    val engineMode: String = "gemini_vision",
    val customCnnUrl: String = "",
    val driveFolderId: String = "1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN",
    val driveDirectoryPath: String = "Drive/Butterfly Dataset/",
    val speciesModelFile: String = "butterfly_species.keras",
    val minConfidence: Double = ClassificationTaxonomy.DEFAULT_MIN_CONFIDENCE,
    val updatedAt: Timestamp? = null
) {
    fun toWriteMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "engineMode" to if (engineMode == "custom_cnn") "custom_cnn" else "gemini_vision",
        "customCnnUrl" to customCnnUrl.trim().take(300),
        "driveFolderId" to driveFolderId.trim().ifBlank { "1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN" }.take(128),
        "driveDirectoryPath" to driveDirectoryPath.trim().ifBlank { "Drive/Butterfly Dataset/" }.take(200),
        "speciesModelFile" to speciesModelFile.trim().ifBlank { "butterfly_species.keras" }.take(100),
        "minConfidence" to minConfidence.coerceIn(0.1, 0.95),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class SpeciesDiscoverySummary(
    val officialLabel: String,
    val commonName: String,
    val scientificName: String,
    val count: Int,
    val bestConfidence: Double,
    val lastSeenMillis: Long
)
