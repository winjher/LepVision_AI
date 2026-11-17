package com.example.data.model

import android.content.Context
import org.json.JSONObject

data class KerasModelSpec(
    val task: ClassificationTask,
    val assetFileName: String,
    val driveDatasetFolderId: String,
    val driveDatasetUrl: String,
    val inputWidth: Int,
    val inputHeight: Int,
    val inputChannels: Int,
    val hasAugmentationBlock: Boolean,
    val augmentationLayers: List<String>,
    val hasInternalRescaling: Boolean,
    val rescalingFactor: Double?,
    val convFilters: List<Int>,
    val denseHiddenUnits: List<Int>,
    val dropoutRate: Double?,
    val outputUnits: Int,
    val outputActivation: String,
    val lossName: String,
    val fromLogits: Boolean,
    val expectedCatalogClasses: Int
) {
    val hasClassCountMismatch: Boolean
        get() = outputUnits > 0 && outputUnits != expectedCatalogClasses

    val expectsExternalNormalization: Boolean
        get() = !hasInternalRescaling

    val architectureSummary: String
        get() {
            val convStr = if (convFilters.isEmpty()) "CNN" else "Conv(${convFilters.joinToString("→")})"
            val hiddenStr = denseHiddenUnits.firstOrNull()?.let { "Dense($it)" } ?: "Dense"
            val normStr = if (hasInternalRescaling) "Rescale(1/255)" else "External 0..1 Norm"
            val outMode = if (fromLogits || outputActivation == "linear") "logits" else outputActivation
            return "${inputWidth}×${inputHeight}×${inputChannels} • $normStr • $convStr → $hiddenStr → $outputUnits $outMode"
        }

    val mismatchAdvisory: String?
        get() = if (hasClassCountMismatch) {
            "Attached config $assetFileName outputs $outputUnits classes ($outputActivation), while Marinduque ${task.displayTitle} defines $expectedCatalogClasses classes. Update the final Dense layer to $expectedCatalogClasses units before retraining on Drive folder $driveDatasetFolderId."
        } else {
            null
        }
}

object KerasArchitectureParser {
    const val SPECIES_ASSET_FILE = "butterfly_data_31c8c23e.json"
    const val STAGES_ASSET_FILE = "butterfly_stages_19e32361.json"

    const val SPECIES_DRIVE_FOLDER_ID = "1CGkRwxh7XSNFlM9XcSZLQ--FLaS4MLSU"
    const val STAGES_DRIVE_FOLDER_ID = "1Pgv0D6D24JEgkfRaJSWETdYMsZlqESet"

    fun parseFromJson(
        jsonString: String,
        task: ClassificationTask,
        assetFileName: String,
        driveDatasetFolderId: String,
        expectedCatalogClasses: Int
    ): KerasModelSpec {
        val root = JSONObject(jsonString)
        val config = root.getJSONObject("config")
        val layers = config.getJSONArray("layers")

        var inputHeight = 224
        var inputWidth = 224
        var inputChannels = 3

        val buildInputShape = config.optJSONArray("build_input_shape")
        if (buildInputShape != null && buildInputShape.length() >= 4) {
            inputHeight = buildInputShape.optInt(1, 224)
            inputWidth = buildInputShape.optInt(2, 224)
            inputChannels = buildInputShape.optInt(3, 3)
        }

        var hasAugmentationBlock = false
        val augmentationLayers = mutableListOf<String>()
        var hasInternalRescaling = false
        var rescalingFactor: Double? = null
        val convFilters = mutableListOf<Int>()
        val denseUnits = mutableListOf<Int>()
        val denseActivations = mutableListOf<String>()
        var dropoutRate: Double? = null

        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val className = layer.optString("class_name")
            val layerConfig = layer.optJSONObject("config") ?: continue

            when (className) {
                "InputLayer" -> {
                    val batchShape = layerConfig.optJSONArray("batch_shape")
                    if (batchShape != null && batchShape.length() >= 4) {
                        inputHeight = batchShape.optInt(1, inputHeight)
                        inputWidth = batchShape.optInt(2, inputWidth)
                        inputChannels = batchShape.optInt(3, inputChannels)
                    }
                }
                "Functional" -> {
                    val subLayers = layerConfig.optJSONArray("layers")
                    if (subLayers != null) {
                        for (j in 0 until subLayers.length()) {
                            val subName = subLayers.getJSONObject(j).optString("class_name")
                            if (subName.startsWith("Random")) {
                                hasAugmentationBlock = true
                                augmentationLayers.add(subName)
                            }
                        }
                    }
                }
                "Rescaling" -> {
                    hasInternalRescaling = true
                    rescalingFactor = layerConfig.optDouble("scale", 1.0 / 255.0)
                }
                "Conv2D" -> {
                    convFilters.add(layerConfig.optInt("filters", 0))
                }
                "Dropout" -> {
                    dropoutRate = layerConfig.optDouble("rate", 0.0)
                }
                "Dense" -> {
                    denseUnits.add(layerConfig.optInt("units", 0))
                    denseActivations.add(layerConfig.optString("activation", "linear"))
                }
            }
        }

        val compileConfig = root.optJSONObject("compile_config")
        var lossName = "categorical_crossentropy"
        var fromLogits = false
        if (compileConfig != null) {
            val rawLoss = compileConfig.opt("loss")
            when (rawLoss) {
                is String -> lossName = rawLoss
                is JSONObject -> {
                    lossName = rawLoss.optString("class_name", lossName)
                    fromLogits = rawLoss.optJSONObject("config")?.optBoolean("from_logits", false) ?: false
                }
            }
        }

        val outputUnits = denseUnits.lastOrNull() ?: expectedCatalogClasses
        val outputActivation = denseActivations.lastOrNull() ?: "softmax"
        val hiddenDense = if (denseUnits.size > 1) denseUnits.dropLast(1) else emptyList()

        return KerasModelSpec(
            task = task,
            assetFileName = assetFileName,
            driveDatasetFolderId = driveDatasetFolderId,
            driveDatasetUrl = "https://drive.google.com/drive/folders/$driveDatasetFolderId",
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            inputChannels = inputChannels,
            hasAugmentationBlock = hasAugmentationBlock,
            augmentationLayers = augmentationLayers,
            hasInternalRescaling = hasInternalRescaling,
            rescalingFactor = rescalingFactor,
            convFilters = convFilters,
            denseHiddenUnits = hiddenDense,
            dropoutRate = dropoutRate,
            outputUnits = outputUnits,
            outputActivation = outputActivation,
            lossName = lossName,
            fromLogits = fromLogits || outputActivation == "linear",
            expectedCatalogClasses = expectedCatalogClasses
        )
    }

    fun loadRegistryFromAssets(context: Context): Map<ClassificationTask, KerasModelSpec> {
        val speciesJson = context.assets.open(SPECIES_ASSET_FILE).bufferedReader().use { it.readText() }
        val stagesJson = context.assets.open(STAGES_ASSET_FILE).bufferedReader().use { it.readText() }

        val speciesSpec = parseFromJson(
            jsonString = speciesJson,
            task = ClassificationTask.SPECIES,
            assetFileName = SPECIES_ASSET_FILE,
            driveDatasetFolderId = SPECIES_DRIVE_FOLDER_ID,
            expectedCatalogClasses = ClassificationTaxonomy.SPECIES_LIST.size
        )
        val stageSpec = parseFromJson(
            jsonString = stagesJson,
            task = ClassificationTask.STAGE,
            assetFileName = STAGES_ASSET_FILE,
            driveDatasetFolderId = STAGES_DRIVE_FOLDER_ID,
            expectedCatalogClasses = ClassificationTaxonomy.STAGE_LIST.size
        )
        return mapOf(
            ClassificationTask.SPECIES to speciesSpec,
            ClassificationTask.STAGE to stageSpec
        )
    }
}
