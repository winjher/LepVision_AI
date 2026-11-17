package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ClassificationTask
import com.example.data.model.KerasArchitectureParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LepVision", appName)
  }

  @Test
  fun `loadRegistryFromAssets parses bundled species and stages keras json`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val registry = KerasArchitectureParser.loadRegistryFromAssets(context)

    val species = registry.getValue(ClassificationTask.SPECIES)
    assertEquals(224, species.inputWidth)
    assertEquals(224, species.inputHeight)
    assertEquals(listOf(32, 64, 128), species.convFilters)
    assertEquals(listOf(512), species.denseHiddenUnits)
    assertEquals(3, species.outputUnits)
    assertEquals("softmax", species.outputActivation)
    assertFalse(species.hasInternalRescaling)
    assertTrue(species.hasClassCountMismatch)
    assertEquals("1CGkRwxh7XSNFlM9XcSZLQ--FLaS4MLSU", species.driveDatasetFolderId)

    val stages = registry.getValue(ClassificationTask.STAGE)
    assertEquals(180, stages.inputWidth)
    assertEquals(180, stages.inputHeight)
    assertTrue(stages.hasAugmentationBlock)
    assertTrue(stages.hasInternalRescaling)
    assertEquals(listOf(16, 32, 64), stages.convFilters)
    assertEquals(listOf(128), stages.denseHiddenUnits)
    assertEquals(4, stages.outputUnits)
    assertTrue(stages.fromLogits)
    assertFalse(stages.hasClassCountMismatch)
    assertEquals("1Pgv0D6D24JEgkfRaJSWETdYMsZlqESet", stages.driveDatasetFolderId)
  }
}
