package com.example.simplecamera.ui.camera

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.simplecamera.R
import com.example.simplecamera.databinding.FragmentCameraBinding
import com.example.simplecamera.ui.camera.CameraManager
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraManager: CameraManager
    private var isVideoMode = false
    private var blinkAnimator: ObjectAnimator? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            cameraManager.startCamera()
        } else {
            Toast.makeText(context, "Нужны разрешения", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraManager = CameraManager(
            context = requireContext(),
            previewView = binding.viewFinder,
            lifecycleOwner = viewLifecycleOwner,
            onVideoRecordEvent = ::handleVideoEvent,
            onPhotoSaved = {
                flashScreen()
                Toast.makeText(context, "Фото сохранено", Toast.LENGTH_SHORT).show()
            },
            onError = { msg -> Log.e("CameraFragment", msg) },
            onFocus = { x, y ->
                val globalX = x + binding.viewFinder.x
                val globalY = y + binding.viewFinder.y
                showFocusRing(globalX, globalY)
            }
        )

        setupInsets()
        setupListeners()

        if (allPermissionsGranted()) {
            cameraManager.startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }

        updateFlashButtonState()
    }

    private fun setupListeners() {
        binding.apply {
            imageCaptureButton.setOnClickListener {
                if (isVideoMode) toggleVideoRecording() else cameraManager.takePhoto()
            }

            cameraSwitchButton.setOnClickListener {
                cameraManager.toggleCamera()
                updateFlashButtonState()
            }

            btnAspectRatio.setOnClickListener {
                if (cameraManager.isRecording()) return@setOnClickListener

                val newRatio = if (binding.btnAspectRatio.text == "3:4") AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
                cameraManager.setFormat(newRatio)

                binding.btnAspectRatio.text = if (newRatio == AspectRatio.RATIO_4_3) "3:4" else "9:16"
            }

            btnFlash.setOnClickListener {
                if (cameraManager.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA) return@setOnClickListener
                val mode = cameraManager.toggleFlash(isVideoMode)
                btnFlash.setImageResource(
                    if (mode == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
                )
            }

            galleryButton.setOnClickListener {
                findNavController().navigate(R.id.action_camera_to_gallery)
            }

            modeToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val newModeVideo = (checkedId == R.id.btn_mode_video)

                    if (isVideoMode != newModeVideo) {
                        isVideoMode = newModeVideo
                        cameraManager.setMode(isVideoMode)
                        updateUIForMode()
                    }
                }
            }
        }
    }

    private fun updateFlashButtonState() {
        val isFrontCamera = cameraManager.cameraSelector == CameraSelector.DEFAULT_FRONT_CAMERA

        if (isFrontCamera) {
            binding.btnFlash.alpha = 0.5f
            binding.btnFlash.isEnabled = false
            binding.btnFlash.setImageResource(R.drawable.ic_flash_off)
            if (cameraManager.flashMode != ImageCapture.FLASH_MODE_OFF) {
                cameraManager.toggleFlash(isVideoMode)
            }
        } else {
            binding.btnFlash.alpha = 1.0f
            binding.btnFlash.isEnabled = true
            binding.btnFlash.setImageResource(
                if (cameraManager.flashMode == ImageCapture.FLASH_MODE_ON) R.drawable.ic_flash_on else R.drawable.ic_flash_off
            )
        }
    }

    private fun toggleVideoRecording() {
        if (cameraManager.isRecording()) {
            cameraManager.stopRecording()
        } else {
            cameraManager.startRecording()
        }
    }

    private fun handleVideoEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                setUiEnabled(false)
                animateRecordButton(true)
                binding.recordingPanel.visibility = View.VISIBLE
                startBlinking()
                binding.root.keepScreenOn = true
            }
            is VideoRecordEvent.Status -> {
                val time = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                binding.timerText.text = String.format("%02d:%02d", time / 60, time % 60)
            }
            is VideoRecordEvent.Finalize -> {
                setUiEnabled(true)
                animateRecordButton(false)
                binding.recordingPanel.visibility = View.GONE
                stopBlinking()
                binding.root.keepScreenOn = false
                if (!event.hasError()) {
                    Toast.makeText(context, "Видео сохранено", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateUIForMode() {
        val strokeColor = if (isVideoMode) Color.RED else Color.WHITE
        binding.imageCaptureButton.strokeColor = ColorStateList.valueOf(strokeColor)
        binding.imageCaptureButton.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.transparent)

        if (!cameraManager.isRecording()) {
            binding.recordingPanel.visibility = View.GONE
            stopBlinking()
            val density = resources.displayMetrics.density
            binding.imageCaptureButton.cornerRadius = (35 * density).toInt()
            binding.imageCaptureButton.icon = null
        }
    }

    private fun animateRecordButton(isRecording: Boolean) {
        val button = binding.imageCaptureButton
        val density = resources.displayMetrics.density
        val startRadius = if (isRecording) 35 * density else 8 * density
        val endRadius = if (isRecording) 8 * density else 35 * density

        ValueAnimator.ofFloat(startRadius, endRadius).apply {
            duration = 300L
            addUpdateListener { button.cornerRadius = (it.animatedValue as Float).toInt() }
            start()
        }
    }

    private fun flashScreen() {
        binding.flashOverlay.visibility = View.VISIBLE
        binding.flashOverlay.postDelayed({ binding.flashOverlay.visibility = View.GONE }, 100L)
    }

    private fun startBlinking() {
        blinkAnimator = ObjectAnimator.ofFloat(binding.recordingIndicator, "alpha", 1f, 0f, 1f).apply {
            duration = 1000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            start()
        }
    }

    private fun stopBlinking() {
        blinkAnimator?.cancel()
        binding.recordingIndicator.alpha = 1f
    }

    private fun showFocusRing(x: Float, y: Float) {
        binding.focusRing.apply {
            this.x = x - (width / 2)
            this.y = y - (height / 2)
            visibility = View.VISIBLE
            alpha = 1f

            animate()
                .alpha(0f)
                .setDuration(500L)
                .setStartDelay(200L)
                .withEndAction { visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        binding.modeToggle.isEnabled = enabled
        binding.btnAspectRatio.isEnabled = enabled
        binding.galleryButton.isEnabled = enabled
        val alpha = if (enabled) 1.0f else 0.5f
        binding.modeToggle.alpha = alpha
        binding.btnAspectRatio.alpha = alpha
        binding.galleryButton.alpha = alpha

        updateFlashButtonState()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val marginButtons = (16 * density).toInt()
            val marginCameraTop = systemBars.top + (70 * density).toInt()

            binding.btnFlash.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = systemBars.top + marginButtons }
            binding.btnAspectRatio.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = systemBars.top + marginButtons }
            binding.recordingPanel.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = marginCameraTop + (16 * density).toInt() }
            binding.viewFinder.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = marginCameraTop }
            insets
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraManager.shutdown()
        stopBlinking()
        _binding = null
    }

    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }
}