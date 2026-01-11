package com.example.simplecamera.ui.viewer

import android.os.Bundle
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.example.simplecamera.databinding.FragmentViewerBinding
import com.example.simplecamera.ui.gallery.GalleryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class ViewerFragment : Fragment() {
    private var _binding: FragmentViewerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by activityViewModels()

    private var isUiVisible = true
    private var wasPlayingBeforeSeek = false
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val list = viewModel.mediaList.value
        if (list.isNullOrEmpty()) {
            findNavController().popBackStack()
            return
        }

        showContent(viewModel.currentPosition)
        setupGestures()
        setupControls()
        startSeekBarUpdater()
    }

    private fun startSeekBarUpdater() {
        viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                if (_binding != null && binding.videoView.isPlaying) {
                    val current = binding.videoView.currentPosition
                    binding.videoSeekBar.progress = current
                    binding.tvCurrentTime.text = formatTime(current)
                }
                delay(500)
            }
        }
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnDelete.setOnClickListener { deleteCurrentMedia() }

        binding.videoSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = formatTime(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                wasPlayingBeforeSeek = binding.videoView.isPlaying
                binding.videoView.pause()
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    binding.videoView.seekTo(it.progress)
                    if (wasPlayingBeforeSeek) {
                        binding.videoView.start()
                        binding.iconPlayCenter.visibility = View.GONE
                    } else {
                        binding.iconPlayCenter.visibility = View.VISIBLE
                        binding.tvCurrentTime.text = formatTime(it.progress)
                    }
                }
            }
        })
    }

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(
            requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                private val SWIPE_THRESHOLD = 100
                private val SWIPE_VELOCITY_THRESHOLD = 100

                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val list = viewModel.mediaList.value ?: return false
                    val item = list.getOrNull(viewModel.currentPosition)
                    if (item?.isVideo == true) {
                        toggleVideoPlayback()
                    } else {
                        toggleUiVisibility()
                    }
                    return true
                }

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y

                    if (abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_THRESHOLD
                        && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) loadPrevious() else loadNext()
                        return true
                    }
                    return false
                }
            })

        val touchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
        binding.container.setOnTouchListener(touchListener)
        binding.videoView.setOnTouchListener(touchListener)
    }

    private fun toggleVideoPlayback() {
        if (binding.videoView.isPlaying) {
            binding.videoView.pause()
            binding.iconPlayCenter.visibility = View.VISIBLE
            if (!isUiVisible) toggleUiVisibility()
        } else {
            binding.videoView.start()
            binding.iconPlayCenter.visibility = View.GONE
        }
    }

    private fun deleteCurrentMedia() {
        val list = viewModel.mediaList.value ?: return
        val pos = viewModel.currentPosition
        if (pos !in list.indices) return

        val item = list[pos]
        try {
            val rowsDeleted = requireContext().contentResolver.delete(item.uri, null, null)
            if (rowsDeleted > 0) {
                Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                viewModel.deleteItem(pos)

                val newList = viewModel.mediaList.value
                if (newList.isNullOrEmpty()) {
                    findNavController().popBackStack()
                } else {
                    if (viewModel.currentPosition >= newList.size) {
                        viewModel.currentPosition = newList.size - 1
                    }
                    showContent(viewModel.currentPosition)
                }
            } else {
                Toast.makeText(context, "Не удалось удалить", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPrevious() {
        if (viewModel.currentPosition > 0) {
            viewModel.currentPosition--
            showContent(viewModel.currentPosition)
        }
    }

    private fun loadNext() {
        val list = viewModel.mediaList.value ?: return
        if (viewModel.currentPosition < list.size - 1) {
            viewModel.currentPosition++
            showContent(viewModel.currentPosition)
        }
    }

    private fun showContent(position: Int) {
        val list = viewModel.mediaList.value ?: return
        val item = list.getOrNull(position) ?: return

        binding.videoView.stopPlayback()
        binding.iconPlayCenter.visibility = View.GONE

        binding.imageView.alpha = 0f
        binding.videoView.alpha = 0f

        if (item.isVideo) {
            binding.imageView.visibility = View.GONE
            binding.videoView.visibility = View.VISIBLE
            binding.videoControlsPanel.visibility = if (isUiVisible) View.VISIBLE else View.GONE

            binding.videoView.setVideoURI(item.uri)
            binding.videoView.setOnPreparedListener { mp ->
                mp.isLooping = true
                binding.videoView.start()
                binding.videoSeekBar.max = binding.videoView.duration
                binding.tvTotalTime.text = formatTime(binding.videoView.duration)

                binding.videoView.animate().alpha(1f).setDuration(300).start()
            }
        } else {
            binding.videoView.visibility = View.GONE
            binding.videoControlsPanel.visibility = View.GONE
            binding.imageView.visibility = View.VISIBLE

            Glide.with(this).load(item.uri).fitCenter().into(binding.imageView)
            binding.imageView.animate().alpha(1f).setDuration(300).start()
        }
    }

    private fun toggleUiVisibility() {
        isUiVisible = !isUiVisible
        val visibility = if (isUiVisible) View.VISIBLE else View.GONE
        binding.btnBack.visibility = visibility
        binding.btnDelete.visibility = visibility

        val list = viewModel.mediaList.value
        val currentItem = list?.getOrNull(viewModel.currentPosition)
        if (currentItem?.isVideo == true) {
            binding.videoControlsPanel.visibility = visibility
        }
    }

    private fun formatTime(millis: Int): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis.toLong())
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%02d:%02d", minutes, remainingSeconds)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.videoView.stopPlayback()
        _binding = null
    }
}