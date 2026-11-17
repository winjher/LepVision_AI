package com.example

import com.example.data.model.ClassificationTask
import com.example.data.model.ClassificationTaxonomy
import com.example.data.model.RiskLevel
import com.example.data.model.UserPreferences
import com.example.data.model.YieldCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LepVisionDomainTest {

    @Test
    fun matchOfficialClass_normalizesPrefixAndFormatting() {
        assertEquals(
            "Butterfly-Golden Birdwing",
            ClassificationTaxonomy.matchOfficialClass("golden birdwing", ClassificationTask.SPECIES)
        )
        assertEquals(
            "Butterfly-Emerald Swallowtail",
            ClassificationTaxonomy.matchOfficialClass("Butterfly-Emerald-Swallowtail", ClassificationTask.SPECIES)
        )
        assertEquals(
            "3rd instar",
            ClassificationTaxonomy.matchOfficialClass("3rd Instar", ClassificationTask.INSTAR)
        )
        assertEquals(
            "Defective",
            ClassificationTaxonomy.matchOfficialClass("defective", ClassificationTask.DEFECT)
        )
        assertNull(
            ClassificationTaxonomy.matchOfficialClass("Unrelated Insect", ClassificationTask.SPECIES)
        )
    }

    @Test
    fun yieldCalculator_flagsDiseaseOutbreakAndStressedHostPlant() {
        val optimal = YieldCalculator.computeYield(
            initialCount = 100,
            healthyLarvae = 95,
            diseasedLarvae = 5,
            goodPupae = 88,
            defectivePupae = 4,
            hostPlantHealth = "Optimal"
        )
        assertEquals(RiskLevel.OPTIMAL, optimal.riskLevel)
        assertTrue(optimal.predictedYieldPercent >= 80.0)

        val critical = YieldCalculator.computeYield(
            initialCount = 100,
            healthyLarvae = 40,
            diseasedLarvae = 45,
            goodPupae = 20,
            defectivePupae = 15,
            hostPlantHealth = "Stressed"
        )
        assertEquals(RiskLevel.CRITICAL, critical.riskLevel)
        assertTrue(critical.recommendations.size >= 2)
    }

    @Test
    fun driveAndKerasDefaults_mapToWriteFields() {
        assertEquals("butterfly_species.keras", ClassificationTask.SPECIES.kerasModelFile)
        assertEquals("butterfly_stages.keras", ClassificationTask.STAGE.kerasModelFile)
        val prefs = UserPreferences(userId = "uid_1")
        val map = prefs.toWriteMap("uid_1")
        assertEquals("1vF4pPNKDG-2hZA4cvC1TLto0h416GKrN", map["driveFolderId"])
        assertEquals("Drive/Butterfly Dataset/", map["driveDirectoryPath"])
        assertEquals("butterfly_species.keras", map["speciesModelFile"])
    }
}
