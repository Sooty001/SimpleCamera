package com.example.simplecamera.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.simplecamera.data.MediaStoreUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS;

class CameraManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val onVideoRecordEvent: (VideoRecordEvent) -> Unit,
    private val onPhotoSaved: () -> Unit,
    private val onError: (String) -> Unit,
    private val onFocus: (Float, Float) -> Unit
) {

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var recording: Recording? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var flashMode: Int = ImageCapture.FLASH_MODE_OFF
    var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    var aspectRatio: Int = AspectRatio.RATIO_4_3

    private var isVideoMode: Boolean = false

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    fun setMode(isVideo: Boolean) {
        if (this.isVideoMode == isVideo) return
        this.isVideoMode = isVideo
        bindCameraUseCases()
    }

    private fun createUseCases() {
        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy(aspectRatio,
                AspectRatioStrategy.FALLBACK_RULE_AUTO))
            .build()

        preview = Preview.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
            .apply { setSurfaceProvider(previewView.surfaceProvider) }

        imageCapture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .setResolutionSelector(resolutionSelector)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .setAspectRatio(aspectRatio)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        if (preview == null || imageCapture == null || videoCapture == null) {
            createUseCases()
        }

        preview?.setSurfaceProvider(previewView.surfaceProvider)

        try {
            provider.unbindAll()

            val useCases = mutableListOf<UseCase>()

            preview?.let { useCases.add(it) }

            if (isVideoMode) {
                videoCapture?.let { useCases.add(it) }
            } else {
                imageCapture?.let { useCases.add(it) }
            }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            setupZoomAndTapToFocus()
            updateTorchState(isVideoMode)
        } catch (exc: Exception) {
            onError("Ошибка привязки камеры: ${exc.message}")
        }
    }

    fun takePhoto() {
        val imageCapture = imageCapture ?: return
        if (isVideoMode) return

        val outputOptions = MediaStoreUtils.getPhotoOutputOptions(context)

        imageCapture.takePicture(outputOptions, cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    onError("Ошибка фото: ${exc.message}")
                }
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    ContextCompat.getMainExecutor(context).execute { onPhotoSaved() }
                }
            })
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val videoCapture = this.videoCapture ?: return
        if (!isVideoMode) return

        val outputOptions = MediaStoreUtils.getVideoOutputOptions(context)

        recording = videoCapture.output
            .prepareRecording(context, outputOptions)
            .apply { withAudioEnabled() }
            .asPersistentRecording()
            .start(cameraExecutor) { event ->
                ContextCompat.getMainExecutor(context).execute {
                    onVideoRecordEvent(event)
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    fun isRecording() = recording != null

    fun toggleCamera() {
        cameraSelector = if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    fun setFormat(ratio: Int) {
        if (isRecording()) return
        aspectRatio = ratio
        preview = null
        imageCapture = null
        videoCapture = null
        startCamera()
    }

    fun toggleFlash(isVideoMode: Boolean): Int {
        flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        imageCapture?.flashMode = flashMode
        updateTorchState(isVideoMode)
        return flashMode
    }

    fun updateTorchState(isVideoMode: Boolean = false) {
        try {
            if (isVideoMode && flashMode == ImageCapture.FLASH_MODE_ON) {
                camera?.cameraControl?.enableTorch(true)
            } else {
                camera?.cameraControl?.enableTorch(false)
            }
        } catch (e: Exception) {
            Log.e("CameraManager", "Torch error", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupZoomAndTapToFocus() {
        val cam = camera ?: return
        var isZooming = false
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isZooming = true
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                cam.cameraControl.setZoomRatio(currentZoom * detector.scaleFactor)
                return true
            }
        })

        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)

            if (event.action == MotionEvent.ACTION_UP) {
                if (isZooming) {
                    isZooming = false
                    return@setOnTouchListener true
                }

                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)

                val action = FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(3, SECONDS)
                    .build()

                try {
                    cam.cameraControl.startFocusAndMetering(action)
                } catch (e: Exception) { }

                onFocus(event.x, event.y)
            }
            true
        }
    }

    fun shutdown() {
        cameraExecutor.shutdown()
    }
}