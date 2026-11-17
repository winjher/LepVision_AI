package com.example.data.model

import kotlin.math.roundToLong

enum class RiskLevel(val label: String) {
    OPTIMAL("Optimal Yield"),
    WATCH("Moderate Risk"),
    CRITICAL("High Attrition Risk")
}

data class YieldForecast(
    val larvalHealthRate: Double,
    val pupalViabilityRate: Double,
    val hostPlantFactor: Double,
    val predictedYieldPercent: Double,
    val predictedAdultCount: Long,
    val riskLevel: RiskLevel,
    val recommendations: List<String>
)

object YieldCalculator {
    fun computeYield(
        initialCount: Long,
        healthyLarvae: Long,
        diseasedLarvae: Long,
        goodPupae: Long,
        defectivePupae: Long,
        hostPlantHealth: String
    ): YieldForecast {
        val safeInitial = initialCount.coerceAtLeast(1L)

        val totalLarvaeObserved = healthyLarvae + diseasedLarvae
        val larvalHealthRate = if (totalLarvaeObserved > 0L) {
            healthyLarvae.toDouble() / totalLarvaeObserved.toDouble()
        } else {
            0.88
        }

        val totalPupaeObserved = goodPupae + defectivePupae
        val pupalViabilityRate = if (totalPupaeObserved > 0L) {
            goodPupae.toDouble() / totalPupaeObserved.toDouble()
        } else {
            0.90
        }

        val hostFactor = when (hostPlantHealth) {
            "Optimal" -> 1.00
            "Moderate" -> 0.87
            "Stressed" -> 0.68
            else -> 0.90
        }

        // Disease spread penalty when >15% of observed larvae show infection
        val diseaseRate = if (totalLarvaeObserved > 0L) diseasedLarvae.toDouble() / totalLarvaeObserved else 0.0
        val outbreakPenalty = when {
            diseaseRate >= 0.30 -> 0.78
            diseaseRate >= 0.15 -> 0.90
            else -> 1.00
        }

        val rawYieldRatio = (larvalHealthRate * pupalViabilityRate * hostFactor * outbreakPenalty)
            .coerceIn(0.0, 1.0)
        val yieldPercent = (rawYieldRatio * 100.0).let { kotlin.math.round(it * 10.0) / 10.0 }

        // If pupae have already been counted, base viable adults on remaining good pupae + remaining larvae potential;
        // otherwise scale initial cohort by predicted yield ratio.
        val projectedAdults = if (totalPupaeObserved >= safeInitial * 0.5) {
            (goodPupae.toDouble() * hostFactor * if (diseaseRate >= 0.25) 0.92 else 0.97)
                .roundToLong()
                .coerceIn(0L, safeInitial)
        } else {
            (safeInitial.toDouble() * rawYieldRatio)
                .roundToLong()
                .coerceIn(0L, safeInitial)
        }

        val riskLevel = when {
            yieldPercent >= 75.0 && diseaseRate < 0.15 -> RiskLevel.OPTIMAL
            yieldPercent >= 52.0 && diseaseRate < 0.30 -> RiskLevel.WATCH
            else -> RiskLevel.CRITICAL
        }

        val recommendations = buildList {
            if (diseasedLarvae > 0L && diseaseRate >= 0.12) {
                add("Isolate $diseasedLarvae diseased larvae immediately to prevent nuclear polyhedrosis or fungal spread.")
            }
            if (defectivePupae > 0L && pupalViabilityRate < 0.85) {
                add("Inspect pupation enclosure humidity (target 70–80% RH) to reduce wing & cremaster defects.")
            }
            if (hostPlantHealth != "Optimal") {
                add("Replenish fresh tender host leaves; stressed foliage lowers 4th–5th instar survival.")
            }
            if (isEmpty()) {
                add("Cohort vitals are strong. Maintain morning misting and clean frass trays daily.")
            }
        }

        return YieldForecast(
            larvalHealthRate = larvalHealthRate,
            pupalViabilityRate = pupalViabilityRate,
            hostPlantFactor = hostFactor,
            predictedYieldPercent = yieldPercent,
            predictedAdultCount = projectedAdults,
            riskLevel = riskLevel,
            recommendations = recommendations
        )
    }
}
