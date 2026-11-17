package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.example.data.model.ClassificationRecord
import com.example.data.model.ClassificationTask
import com.example.data.model.ClassificationTaxonomy
import com.example.data.model.KerasModelSpec
import com.example.data.remote.CnnApiService
import com.example.data.remote.CnnClassifyRequest
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream

data class ClassificationOutput(
    val record: ClassificationRecord,
    val diagnosticSummary: String
)

class ClassificationAiRepository {

    private val fullScanSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "species" to Schema.string(
                    description = "Exact official Marinduque species label from the allowed list, or 'Unknown' if not a butterfly/moth or uncertain."
                ),
                "speciesConfidence" to Schema.double(
                    description = "Confidence score 0.0 to 1.0 for species."
                ),
                "stage" to Schema.enumeration(
                    values = listOf("Egg", "Larva", "Pupa", "Adult", "Unknown"),
                    description = "Lifecycle stage of the specimen."
                ),
                "stageConfidence" to Schema.double(
                    description = "Confidence score 0.0 to 1.0 for lifecycle stage."
                ),
                "disease" to Schema.enumeration(
                    values = listOf("Healthy", "Diseased", "N/A"),
                    description = "Larval disease status if stage is Larva, otherwise 'N/A'."
                ),
                "instar" to Schema.enumeration(
                    values = listOf("1st instar", "2nd instar", "3rd instar", "4th instar", "5th instar", "N/A"),
                    description = "Larval instar if stage is Larva, otherwise 'N/A'."
                ),
                "defect" to Schema.enumeration(
                    values = listOf("Good", "Defective", "N/A"),
                    description = "Pupae structural quality if stage is Pupa, otherwise 'N/A'."
                ),
                "sex" to Schema.enumeration(
                    values = listOf("Male", "Female", "N/A"),
                    description = "Adult wing morphology sex if stage is Adult, otherwise 'N/A'."
                ),
                "summary" to Schema.string(
                    description = "Concise 2-3 sentence conservation & husbandry diagnostic note for a Marinduque butterfly breeder."
                )
            )
        )
    }

    private val singleTaskSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "label" to Schema.string(
                    description = "The best matching label from the allowed class list, or 'Unknown' if uncertain."
                ),
                "confidence" to Schema.double(
                    description = "Prediction confidence from 0.0 to 1.0."
                ),
                "summary" to Schema.string(
                    description = "Short 1-2 sentence visual rationale for the classification."
                )
            )
        )
    }

    private val fullScanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = fullScanSchema
                temperature = 0.2f
            }
        )
    }

    private val singleTaskModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = singleTaskSchema
                temperature = 0.2f
            }
        )
    }

    suspend fun loadDownscaledBitmap(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Unable to read selected specimen image.")

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOpts)

        val maxDim = 1024
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
            ?: throw IllegalArgumentException("Selected file is not a valid image.")

        // Ensure dimensions stay compact (< 1024px) and payload stays well under 20MB
        val scale = minOf(1f, maxDim.toFloat() / maxOf(decoded.width, decoded.height).coerceAtLeast(1))
        if (scale < 1f) {
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }
    }

    suspend fun classifySpecimen(
        bitmap: Bitmap,
        task: ClassificationTask,
        minConfidence: Double,
        engineMode: String,
        customCnnUrl: String,
        driveFolderId: String = "1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN",
        speciesModelFile: String = "butterfly_species.keras",
        kerasSpec: KerasModelSpec? = null,
        batchName: String?
    ): ClassificationOutput {
        if (engineMode == "custom_cnn") {
            val activeModelFile = if (task == ClassificationTask.SPECIES || task == ClassificationTask.FULL_SCAN) {
                speciesModelFile.ifBlank { ClassificationTask.SPECIES.kerasModelFile }
            } else {
                task.kerasModelFile
            }
            if (customCnnUrl.isBlank()) {
                throw IllegalStateException(
                    "Custom CNN Server URL is not configured. Enter your api.py server URL for Drive/Butterfly Dataset/$activeModelFile in Settings, or switch back to Gemini Vision AI."
                )
            }
            return classifyViaCustomCnn(
                bitmap = bitmap,
                requestedTask = task,
                minConfidence = minConfidence,
                baseUrl = customCnnUrl,
                modelFile = activeModelFile,
                driveFolderId = driveFolderId.ifBlank { "1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN" },
                kerasSpec = kerasSpec,
                batchName = batchName
            )
        }
        return if (task == ClassificationTask.FULL_SCAN) {
            classifyFullScanGemini(bitmap, minConfidence, batchName)
        } else {
            classifySingleTaskGemini(bitmap, task, minConfidence, batchName)
        }
    }

    private suspend fun classifyFullScanGemini(
        bitmap: Bitmap,
        minConfidence: Double,
        batchName: String?
    ): ClassificationOutput {
        val allowedSpecies = ClassificationTaxonomy.SPECIES_LIST.joinToString(", ")
        val prompt = """
            You are LepVision, an expert lepidopterist AI assisting butterfly conservation breeders in Marinduque, Philippines.
            Analyze this specimen image carefully.
            1. Species Identification: Match ONLY against this official list: [$allowedSpecies]. If the specimen is an egg/larva/pupa whose species cannot be visually confirmed or is not on the list, return 'Unknown' with lower speciesConfidence.
            2. Lifecycle Stage: Choose one of [Egg, Larva, Pupa, Adult, Unknown].
            3. If Larva: evaluate larval disease [Healthy, Diseased] (look for discoloration, flaccidity, dark lesions, mold, or abnormal posture) and larval instar [1st instar, 2nd instar, 3rd instar, 4th instar, 5th instar]. Otherwise set 'N/A'.
            4. If Pupa: evaluate pupae defect [Good, Defective] (look for crushed shell, dark necrotic spots, malformed wing cases, or broken cremaster). Otherwise set 'N/A'.
            5. If Adult: evaluate gender [Male, Female] by wing morphology/pattern. Otherwise set 'N/A'.
            Provide a concise, practical 2-3 sentence diagnostic summary for the breeder.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                fullScanModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Vision model returned an empty response.")
            val json = JSONObject(jsonText)

            val rawSpecies = json.optString("species", ClassificationTaxonomy.UNKNOWN)
            val speciesConf = json.optDouble("speciesConfidence", 0.0).coerceIn(0.0, 1.0)
            val matchedSpecies = ClassificationTaxonomy.matchOfficialClass(rawSpecies, ClassificationTask.SPECIES)
            val finalSpecies = if (matchedSpecies != null && speciesConf >= minConfidence) {
                matchedSpecies
            } else {
                ClassificationTaxonomy.UNKNOWN
            }

            val rawStage = json.optString("stage", ClassificationTaxonomy.UNKNOWN)
            val stageConf = json.optDouble("stageConfidence", 0.0).coerceIn(0.0, 1.0)
            val matchedStage = ClassificationTaxonomy.matchOfficialClass(rawStage, ClassificationTask.STAGE)
            val finalStage = if (matchedStage != null && stageConf >= minConfidence) {
                matchedStage
            } else {
                ClassificationTaxonomy.UNKNOWN
            }

            val rawDisease = json.optString("disease", "N/A")
            val finalDisease = ClassificationTaxonomy.matchOfficialClass(rawDisease, ClassificationTask.DISEASE) ?: "N/A"

            val rawInstar = json.optString("instar", "N/A")
            val finalInstar = ClassificationTaxonomy.matchOfficialClass(rawInstar, ClassificationTask.INSTAR) ?: "N/A"

            val rawDefect = json.optString("defect", "N/A")
            val finalDefect = ClassificationTaxonomy.matchOfficialClass(rawDefect, ClassificationTask.DEFECT) ?: "N/A"

            val rawSex = json.optString("sex", "N/A")
            val finalSex = ClassificationTaxonomy.matchOfficialClass(rawSex, ClassificationTask.SEX) ?: "N/A"

            val summary = json.optString("summary", "").take(800)

            val primaryLabel = when {
                finalSpecies != ClassificationTaxonomy.UNKNOWN -> finalSpecies
                finalStage != ClassificationTaxonomy.UNKNOWN -> finalStage
                else -> ClassificationTaxonomy.UNKNOWN
            }
            val primaryConf = maxOf(speciesConf, stageConf)
            val isIdentified = primaryLabel != ClassificationTaxonomy.UNKNOWN && primaryConf >= minConfidence

            val record = ClassificationRecord(
                task = ClassificationTask.FULL_SCAN.key,
                label = primaryLabel,
                confidence = primaryConf,
                identified = isIdentified,
                speciesLabel = finalSpecies,
                stageLabel = finalStage,
                diseaseLabel = finalDisease,
                instarLabel = finalInstar,
                defectLabel = finalDefect,
                sexLabel = finalSex,
                summary = summary,
                engineMode = "gemini_vision",
                batchName = batchName?.takeIf { it.isNotBlank() }
            )
            return ClassificationOutput(record = record, diagnosticSummary = summary)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Full scan timed out", e)
            throw IllegalStateException("Vision analysis timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Full scan classification failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Classification failed.", e)
        }
    }

    private suspend fun classifySingleTaskGemini(
        bitmap: Bitmap,
        task: ClassificationTask,
        minConfidence: Double,
        batchName: String?
    ): ClassificationOutput {
        val allowedClasses = ClassificationTaxonomy.classesForTask(task).joinToString(", ")
        val prompt = """
            You are LepVision, a butterfly conservation classifier for Marinduque breeders.
            Task: ${task.analysisType}.
            Allowed class labels (choose EXACTLY one from this list, or 'Unknown' if unclear): [$allowedClasses].
            Inspect the specimen photo and return the best matching label, confidence (0.0 to 1.0), and a brief rationale.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                singleTaskModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Vision model returned an empty response.")
            val json = JSONObject(jsonText)

            val rawLabel = json.optString("label", ClassificationTaxonomy.UNKNOWN)
            val confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            val summary = json.optString("summary", "").take(800)

            val matched = ClassificationTaxonomy.matchOfficialClass(rawLabel, task)
            val identified = matched != null && confidence >= minConfidence
            val acceptedLabel = if (identified) matched!! else ClassificationTaxonomy.UNKNOWN

            val record = ClassificationRecord(
                task = task.key,
                label = acceptedLabel,
                confidence = confidence,
                identified = identified,
                speciesLabel = if (task == ClassificationTask.SPECIES) acceptedLabel else null,
                stageLabel = if (task == ClassificationTask.STAGE) acceptedLabel else null,
                diseaseLabel = if (task == ClassificationTask.DISEASE) acceptedLabel else null,
                instarLabel = if (task == ClassificationTask.INSTAR) acceptedLabel else null,
                defectLabel = if (task == ClassificationTask.DEFECT) acceptedLabel else null,
                sexLabel = if (task == ClassificationTask.SEX) acceptedLabel else null,
                summary = summary,
                engineMode = "gemini_vision",
                batchName = batchName?.takeIf { it.isNotBlank() }
            )
            return ClassificationOutput(record = record, diagnosticSummary = summary)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Single task scan timed out", e)
            throw IllegalStateException("Vision analysis timed out after 60 seconds. Please retry.", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Single task classification failed", e)
            throw IllegalStateException(e.localizedMessage ?: "Classification failed.", e)
        }
    }

    private suspend fun classifyViaCustomCnn(
        bitmap: Bitmap,
        requestedTask: ClassificationTask,
        minConfidence: Double,
        baseUrl: String,
        modelFile: String,
        driveFolderId: String,
        kerasSpec: KerasModelSpec?,
        batchName: String?
    ): ClassificationOutput {
        val effectiveTask = if (requestedTask == ClassificationTask.FULL_SCAN) {
            ClassificationTask.SPECIES
        } else {
            requestedTask
        }
        val base64Image = withContext(Dispatchers.IO) {
            val targetBitmap = if (kerasSpec != null &&
                (bitmap.width != kerasSpec.inputWidth || bitmap.height != kerasSpec.inputHeight)
            ) {
                Bitmap.createScaledBitmap(bitmap, kerasSpec.inputWidth, kerasSpec.inputHeight, true)
            } else {
                bitmap
            }
            val stream = ByteArrayOutputStream()
            targetBitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            if (targetBitmap !== bitmap) {
                targetBitmap.recycle()
            }
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }
        try {
            val service = CnnApiService.create(baseUrl)
            val response = withTimeout(60_000L) {
                service.classifyJson(
                    CnnClassifyRequest(
                        analysisType = effectiveTask.analysisType,
                        imageBase64 = base64Image,
                        mime = "image/jpeg",
                        modelFile = modelFile,
                        driveFolderId = driveFolderId,
                        datasetFolderId = kerasSpec?.driveDatasetFolderId ?: effectiveTask.datasetFolderId,
                        inputSize = kerasSpec?.let { listOf(it.inputWidth, it.inputHeight) },
                        outputActivation = kerasSpec?.outputActivation,
                        expectsNormalizedInput = kerasSpec?.expectsExternalNormalization
                    )
                )
            }
            val (rawLabel, conf) = when (effectiveTask) {
                ClassificationTask.SPECIES -> response.species?.predictions?.firstOrNull()?.let { it.species to it.confidence }
                ClassificationTask.STAGE -> response.stage?.predictions?.firstOrNull()?.let { it.stage to it.confidence }
                ClassificationTask.DISEASE -> response.disease?.predictions?.firstOrNull()?.let { it.disease to it.confidence }
                ClassificationTask.INSTAR -> response.instar?.predictions?.firstOrNull()?.let { it.instar to it.confidence }
                ClassificationTask.DEFECT -> response.defects?.predictions?.firstOrNull()?.let { it.defect to it.confidence }
                ClassificationTask.SEX -> response.sex?.predictions?.firstOrNull()?.let { it.sex to it.confidence }
                ClassificationTask.FULL_SCAN -> null
            } ?: (null to 0.0)

            val matched = ClassificationTaxonomy.matchOfficialClass(rawLabel, effectiveTask)
            val safeConf = conf.coerceIn(0.0, 1.0)
            val identified = matched != null && safeConf >= minConfidence
            val label = if (identified) matched!! else ClassificationTaxonomy.UNKNOWN

            val fullScanNote = if (requestedTask == ClassificationTask.FULL_SCAN) {
                " (routed Full Scan to Species Identification)"
            } else {
                ""
            }
            val specNote = kerasSpec?.let { " [${it.inputWidth}×${it.inputHeight} ${it.outputActivation}]" }.orEmpty()
            val summary = "Classified via custom CNN ($modelFile$specNote • Drive $driveFolderId)$fullScanNote: $label (${(safeConf * 100).toInt()}% confidence)."
            val record = ClassificationRecord(
                task = requestedTask.key,
                label = label,
                confidence = safeConf,
                identified = identified,
                speciesLabel = if (effectiveTask == ClassificationTask.SPECIES) label else null,
                stageLabel = if (effectiveTask == ClassificationTask.STAGE) label else null,
                diseaseLabel = if (effectiveTask == ClassificationTask.DISEASE) label else null,
                instarLabel = if (effectiveTask == ClassificationTask.INSTAR) label else null,
                defectLabel = if (effectiveTask == ClassificationTask.DEFECT) label else null,
                sexLabel = if (effectiveTask == ClassificationTask.SEX) label else null,
                summary = summary,
                engineMode = "custom_cnn",
                batchName = batchName?.takeIf { it.isNotBlank() }
            )
            return ClassificationOutput(record = record, diagnosticSummary = summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Custom CNN classification failed", e)
            throw IllegalStateException("Custom CNN server error: ${e.localizedMessage ?: "Connection failed"}", e)
        }
    }

    suspend fun testCustomCnnConnection(baseUrl: String): Result<String> {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Enter your api.py server URL first (e.g. https://...)."))
        }
        return try {
            val service = CnnApiService.create(trimmed)
            val statusResp = withTimeout(15_000L) { service.checkStatus() }
            if (statusResp.isSuccessful) {
                Result.success("Connected (${statusResp.code()}) via /api/status — api.py is reachable.")
            } else if (statusResp.code() == 404) {
                val rootResp = withTimeout(15_000L) { service.pingRoot() }
                if (rootResp.isSuccessful) {
                    Result.success("Connected (${rootResp.code()}) — server reachable (POST /api/classify-json ready).")
                } else {
                    Result.failure(IllegalStateException("Server responded HTTP ${rootResp.code()} on root endpoint."))
                }
            } else {
                Result.failure(IllegalStateException("Server responded HTTP ${statusResp.code()} on /api/status."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Unable to reach CNN server."))
        }
    }

    private companion object {
        const val TAG = "ClassificationAiRepo"
    }
}
