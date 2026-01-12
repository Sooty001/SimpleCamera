package com.example.simplecamera.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.example.simplecamera.data.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GalleryRepository(private val context: Context) {

    suspend fun getMediaFiles(): List<MediaFile> = withContext(Dispatchers.IO) {
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

        context.contentResolver.query(
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
        return@withContext list
    }

    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val rowsDeleted = context.contentResolver.delete(uri, null, null)
            return@withContext rowsDeleted > 0
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }
}