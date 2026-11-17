package com.example.camera

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.Surface
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Utility class encapsulating CameraX preview binding, flash/lens controls,
 * and JPEG photo capture for the LepVision butterfly classification system.
 */
class CameraHelper(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var activeCamera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        lensFacing: Int = CameraSelector.LENS_FACING_BACK,
        flashMode: Int = ImageCapture.FLASH_MODE_AUTO,
        onBound: (Camera) -> Unit = {},
        onError: (Throwable) -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                val selector = resolveCameraSelector(provider, lensFacing)
                val rotation = previewView.display?.rotation ?: Surface.ROTATION_0

                val preview = Preview.Builder()
                    .setTargetRotation(rotation)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(flashMode)
                    .setTargetRotation(rotation)
                    .build()

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    capture
                )
                imageCapture = capture
                activeCamera = camera
                onBound(camera)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind CameraX use cases", e)
                onError(e)
            }
        }, mainExecutor)
    }

    fun updateFlashMode(flashMode: Int) {
        imageCapture?.flashMode = flashMode
    }

    fun capturePhotoToFile(
        onImageCaptured: (Uri) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val capture = imageCapture
        if (capture == null) {
            onError(IllegalStateException("Camera is not ready yet. Please wait a moment."))
            return
        }

        val outputFile = createTempCaptureFile(context)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        val mainExecutor = ContextCompat.getMainExecutor(context)

        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val uri = outputFileResults.savedUri ?: FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outputFile
                    )
                    mainExecutor.execute {
                        onImageCaptured(uri)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    mainExecutor.execute {
                        onError(exception)
                    }
                }
            }
        )
    }

    fun release() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error unbinding CameraX provider", e)
        } finally {
            activeCamera = null
            imageCapture = null
            cameraExecutor.shutdown()
        }
    }

    private fun resolveCameraSelector(
        provider: ProcessCameraProvider,
        preferredLensFacing: Int
    ): CameraSelector {
        val preferred = CameraSelector.Builder()
            .requireLensFacing(preferredLensFacing)
            .build()
        if (provider.hasCamera(preferred)) {
            return preferred
        }
        val fallbackFacing = if (preferredLensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        val fallback = CameraSelector.Builder()
            .requireLensFacing(fallbackFacing)
            .build()
        return if (provider.hasCamera(fallback)) {
            fallback
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    companion object {
        private const val TAG = "CameraHelper"

        fun createTempCaptureFile(context: Context): File {
            val capturesDir = File(context.cacheDir, "captures").apply { mkdirs() }
            return File(capturesDir, "specimen_${System.currentTimeMillis()}.jpg")
        }

        fun createTempCaptureUri(context: Context): Uri {
            val file = createTempCaptureFile(context)
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }
}
