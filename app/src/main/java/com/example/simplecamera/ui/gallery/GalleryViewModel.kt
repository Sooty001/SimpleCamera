package com.example.simplecamera.ui.gallery

import android.app.Application
import android.content.ContentUris
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.simplecamera.R
import com.example.simplecamera.data.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val _mediaList = MutableLiveData<List<MediaFile>>()
    val mediaList: LiveData<List<MediaFile>> get() = _mediaList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    var currentPosition: Int = 0

    private var allFiles: List<MediaFile> = emptyList()

    fun loadMedia() {
        _isLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<MediaFile>()
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED
            )
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
            val selectionArgs = arrayOf(
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
            )
            val uri = MediaStore.Files.getContentUri("external")

            getApplication<Application>().contentResolver.query(
                uri, projection, selection, selectionArgs, sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val type = cursor.getInt(typeCol)
                    val date = cursor.getLong(dateCol)
                    val contentUri = ContentUris.withAppendedId(uri, id)

                    list.add(
                        MediaFile(
                            id,
                            contentUri,
                            type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
                            date * 1000
                        )
                    )
                }
            }

            allFiles = list
            _mediaList.postValue(list)
            _isLoading.postValue(false)
        }
    }

    fun filter(filterId: Int) {
        val filtered = when (filterId) {
            R.id.chipPhotos -> allFiles.filter { !it.isVideo }
            R.id.chipVideos -> allFiles.filter { it.isVideo }
            else -> allFiles
        }
        _mediaList.value = filtered
    }

    fun deleteItem(index: Int) {
        if (index in allFiles.indices) {
            val newList = _mediaList.value?.toMutableList() ?: return
            if (index < newList.size) {
                newList.removeAt(index)
                _mediaList.value = newList
            }
        }
    }
}