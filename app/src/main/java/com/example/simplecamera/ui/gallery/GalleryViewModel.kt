package com.example.simplecamera.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.simplecamera.R
import com.example.simplecamera.data.model.MediaFile
import com.example.simplecamera.data.repository.GalleryRepository
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GalleryRepository(application.applicationContext)

    private val _mediaList = MutableLiveData<List<MediaFile>>()
    val mediaList: LiveData<List<MediaFile>> get() = _mediaList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    var currentPosition: Int = 0
    private var allFiles: List<MediaFile> = emptyList()

    private var currentFilterId: Int = R.id.chipAll

    fun loadMedia() {
        _isLoading.value = true
        viewModelScope.launch {
            val list = repository.getMediaFiles()
            allFiles = list
            filter(currentFilterId)
            _isLoading.value = false
        }
    }

    fun filter(filterId: Int) {
        currentFilterId = filterId

        val filtered = when (filterId) {
            R.id.chipPhotos -> allFiles.filter { !it.isVideo }
            R.id.chipVideos -> allFiles.filter { it.isVideo }
            else -> allFiles
        }
        _mediaList.value = filtered
    }

    fun deleteCurrentMedia(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val list = _mediaList.value ?: return
        if (currentPosition !in list.indices) return
        val itemToDelete = list[currentPosition]

        viewModelScope.launch {
            val isDeleted = repository.deleteFile(itemToDelete.uri)

            if (isDeleted) {
                allFiles = allFiles.filter { it.id != itemToDelete.id }
                filter(currentFilterId)
                val newList = _mediaList.value ?: emptyList()
                if (currentPosition >= newList.size) {
                    currentPosition = maxOf(0, newList.size - 1)
                }

                onSuccess()
            } else {
                onError("Не удалось удалить файл")
            }
        }
    }
}