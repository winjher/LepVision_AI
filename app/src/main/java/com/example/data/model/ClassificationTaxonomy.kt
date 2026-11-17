package com.example.data.model

enum class ClassificationTask(
    val key: String,
    val folder: String,
    val kerasModelFile: String,
    val datasetFolderId: String?,
    val analysisType: String,
    val blockKey: String,
    val predictionKey: String,
    val displayTitle: String,
    val shortLabel: String,
    val description: String
) {
    FULL_SCAN(
        key = "full_scan",
        folder = "full_scan",
        kerasModelFile = "butterfly_species.keras",
        datasetFolderId = "1CGkRwxh7XSNFlM9XcSZLQ--FLaS4MLSU",
        analysisType = "Full Specimen Assessment",
        blockKey = "full_scan",
        predictionKey = "label",
        displayTitle = "Full Specimen Scan",
        shortLabel = "Full Scan",
        description = "Simultaneously evaluates species, lifecycle stage, larval health, instar, pupae defects, and sex."
    ),
    SPECIES(
        key = "species",
        folder = "butterfly_species",
        kerasModelFile = "butterfly_species.keras",
        datasetFolderId = "1CGkRwxh7XSNFlM9XcSZLQ--FLaS4MLSU",
        analysisType = "Species Identification",
        blockKey = "species",
        predictionKey = "species",
        displayTitle = "Species Identification",
        shortLabel = "Species",
        description = "Identifies 18 Marinduque butterfly & moth species (Drive/Butterfly Dataset/butterfly_species.keras • 224×224 CNN)."
    ),
    STAGE(
        key = "stage",
        folder = "butterfly_stages",
        kerasModelFile = "butterfly_stages.keras",
        datasetFolderId = "1Pgv0D6D24JEgkfRaJSWETdYMsZlqESet",
        analysisType = "Lifecycle Stage",
        blockKey = "stage",
        predictionKey = "stage",
        displayTitle = "Lifecycle Stage",
        shortLabel = "Stage",
        description = "Classifies Egg, Larva, Pupa, or Adult stage (butterfly_stages.keras • 180×180 CNN)."
    ),
    DISEASE(
        key = "disease",
        folder = "larval_diseases",
        kerasModelFile = "larval_diseases.keras",
        datasetFolderId = null,
        analysisType = "Larval Disease Detection",
        blockKey = "disease",
        predictionKey = "disease",
        displayTitle = "Larval Disease",
        shortLabel = "Disease",
        description = "Detects viral/bacterial/fungal disease symptoms in larvae (larval_diseases.keras)."
    ),
    INSTAR(
        key = "instar",
        folder = "larvae_instars",
        kerasModelFile = "larvae_instars.keras",
        datasetFolderId = null,
        analysisType = "Larvae Instar",
        blockKey = "instar",
        predictionKey = "instar",
        displayTitle = "Larvae Instar",
        shortLabel = "Instar",
        description = "Estimates caterpillar growth stage from 1st to 5th instar (larvae_instars.keras)."
    ),
    DEFECT(
        key = "defect",
        folder = "pupae_defects",
        kerasModelFile = "pupae_defects.keras",
        datasetFolderId = null,
        analysisType = "Pupae Defect Analysis",
        blockKey = "defects",
        predictionKey = "defect",
        displayTitle = "Pupae Defect",
        shortLabel = "Pupae",
        description = "Inspects chrysalis shell integrity: Good vs Defective (pupae_defects.keras)."
    ),
    SEX(
        key = "sex",
        folder = "gender_sexing",
        kerasModelFile = "gender_sexing.keras",
        datasetFolderId = null,
        analysisType = "Gender Sexing",
        blockKey = "sex",
        predictionKey = "sex",
        displayTitle = "Gender Sexing",
        shortLabel = "Sex",
        description = "Distinguishes Male vs Female adult wing morphology (gender_sexing.keras)."
    );

    companion object {
        val singleTasks: List<ClassificationTask> = listOf(SPECIES, STAGE, DISEASE, INSTAR, DEFECT, SEX)

        fun fromKey(key: String): ClassificationTask {
            return entries.firstOrNull { it.key == key } ?: FULL_SCAN
        }
    }
}

data class MarinduqueSpeciesInfo(
    val officialLabel: String,
    val commonName: String,
    val scientificName: String,
    val hostPlant: String,
    val conservationNote: String
)

object ClassificationTaxonomy {
    const val UNKNOWN = "Unknown"
    const val DEFAULT_MIN_CONFIDENCE = 0.50

    val SPECIES_LIST = listOf(
        "Butterfly-Batwing",
        "Butterfly-Clippers",
        "Butterfly-Common Jay",
        "Butterfly-Common Lime",
        "Butterfly-Common Mime",
        "Butterfly-Common Mormon",
        "Butterfly-Emerald Swallowtail",
        "Butterfly-Giant Silk Moth",
        "Butterfly-Golden Birdwing",
        "Butterfly-Great Eggfly",
        "Butterfly-Great Yellow Mormon",
        "Butterfly-Grey Glassy Tiger",
        "Butterfly-Paper Kite",
        "Butterfly-Pink Rose",
        "Butterfly-Plain Tiger",
        "Butterfly-Red Lacewing",
        "Butterfly-Scarlet Mormon",
        "Butterfly-Tailed Jay"
    )

    val STAGE_LIST = listOf("Egg", "Larva", "Pupa", "Adult")
    val DISEASE_LIST = listOf("Healthy", "Diseased")
    val INSTAR_LIST = listOf("1st instar", "2nd instar", "3rd instar", "4th instar", "5th instar")
    val DEFECT_LIST = listOf("Good", "Defective")
    val SEX_LIST = listOf("Male", "Female")

    val SPECIES_CATALOG: List<MarinduqueSpeciesInfo> = listOf(
        MarinduqueSpeciesInfo("Butterfly-Batwing", "Batwing", "Atrophaneura semperi", "Aristolochia spp.", "Protected forest-edge swallowtail; sensitive to host vine desiccation."),
        MarinduqueSpeciesInfo("Butterfly-Clippers", "Clippers", "Parthenos sylvia", "Adenia / Tinospora", "Strong canopy flier prized in Marinduque sustainable ranches."),
        MarinduqueSpeciesInfo("Butterfly-Common Jay", "Common Jay", "Graphium doson", "Annona / Polyalthia", "Fast nectar feeder; thriving larvae require tender young leaves."),
        MarinduqueSpeciesInfo("Butterfly-Common Lime", "Common Lime", "Papilio demoleus", "Citrus / Calamansi", "Resilient citrus breeder; monitor 3rd–5th instars for crowding."),
        MarinduqueSpeciesInfo("Butterfly-Common Mime", "Common Mime", "Papilio clytia", "Litsea / Cinnamomum", "Batesian mimic of toxic milkweed butterflies."),
        MarinduqueSpeciesInfo("Butterfly-Common Mormon", "Common Mormon", "Papilio polytes", "Citrus / Murraya", "High female polymorphism; reliable yield baseline species."),
        MarinduqueSpeciesInfo("Butterfly-Emerald Swallowtail", "Emerald Swallowtail", "Papilio palinurus", "Euodia / Melicope", "Flagship iridescent green species; high conservation & export value."),
        MarinduqueSpeciesInfo("Butterfly-Giant Silk Moth", "Giant Silk Moth", "Attacus atlas", "Guava / Avocado / Annona", "Atlas moth; huge pupae require spacious hanging frames for wing expansion."),
        MarinduqueSpeciesInfo("Butterfly-Golden Birdwing", "Golden Birdwing", "Troides rhadamantus", "Aristolochia tagala", "Endemic Philippine birdwing; CITES Appendix II flagship conservation species."),
        MarinduqueSpeciesInfo("Butterfly-Great Eggfly", "Great Eggfly", "Hypolimnas bolina", "Asystasia / Ipomoea", "Vibrant territorial butterfly; rapid egg-to-adult turnover."),
        MarinduqueSpeciesInfo("Butterfly-Great Yellow Mormon", "Great Yellow Mormon", "Papilio lowi", "Citrus maxima", "Large swallowtail requiring abundant pomelo foliage in 4th–5th instar."),
        MarinduqueSpeciesInfo("Butterfly-Grey Glassy Tiger", "Grey Glassy Tiger", "Ideopsis juventa", "Tylophora / Piper", "Translucent danaid; thrives in shaded humid enclosures."),
        MarinduqueSpeciesInfo("Butterfly-Paper Kite", "Paper Kite", "Idea leuconoe", "Parsonsia alboflavescens", "Slow graceful flight; iconic Marinduque butterfly house attraction."),
        MarinduqueSpeciesInfo("Butterfly-Pink Rose", "Pink Rose", "Pachliopta kotzebuea", "Aristolochia spp.", "Endemic Philippine rose swallowtail; monitor pupal humidity carefully."),
        MarinduqueSpeciesInfo("Butterfly-Plain Tiger", "Plain Tiger", "Danaus chrysippus", "Calotropis gigantea", "Milkweed specialist; hardy chrysalis with high emergence rate."),
        MarinduqueSpeciesInfo("Butterfly-Red Lacewing", "Red Lacewing", "Cethosia biblis", "Passiflora spp.", "Ornate forest-edge nymphalid; gregarious early-instar larvae."),
        MarinduqueSpeciesInfo("Butterfly-Scarlet Mormon", "Scarlet Mormon", "Papilio rumanzovia", "Citrus spp.", "Philippine endemic swallowtail with vivid crimson hindwing markings."),
        MarinduqueSpeciesInfo("Butterfly-Tailed Jay", "Tailed Jay", "Graphium agamemnon", "Annona muricata (Guyabano)", "Distinctive lime-green spotted wings; rapid pupal stage (~10 days).")
    )

    fun classesForTask(task: ClassificationTask): List<String> = when (task) {
        ClassificationTask.SPECIES -> SPECIES_LIST
        ClassificationTask.STAGE -> STAGE_LIST
        ClassificationTask.DISEASE -> DISEASE_LIST
        ClassificationTask.INSTAR -> INSTAR_LIST
        ClassificationTask.DEFECT -> DEFECT_LIST
        ClassificationTask.SEX -> SEX_LIST
        ClassificationTask.FULL_SCAN -> SPECIES_LIST
    }

    /**
     * Mirrors clsMatchClass_ from Classification.gs:
     * normalizes label ignoring case, spaces, hyphens, and the "Butterfly-" prefix.
     */
    fun matchOfficialClass(rawLabel: String?, task: ClassificationTask): String? {
        if (rawLabel.isNullOrBlank()) return null
        val norm: (String) -> String = { s ->
            s.lowercase()
                .replace(Regex("^butterfly[-\\s]*"), "")
                .replace(Regex("[^a-z0-9]"), "")
        }
        val target = norm(rawLabel)
        if (target.isBlank()) return null
        return classesForTask(task).firstOrNull { candidate ->
            norm(candidate) == target
        }
    }

    fun shortSpeciesName(officialLabel: String): String {
        return officialLabel.removePrefix("Butterfly-")
    }

    fun speciesInfoFor(officialLabel: String): MarinduqueSpeciesInfo? {
        return SPECIES_CATALOG.firstOrNull { it.officialLabel == officialLabel }
    }
}
