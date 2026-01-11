package com.example.simplecamera.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.video.MediaStoreOutputOptions
import java.text.SimpleDateFormat
import java.util.Locale

object MediaStoreUtils {
    private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    private const val APP_FOLDER = "SimpleCamera"

    private fun createContentValues(mimeType: String, relativePath: String): ContentValues {
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            }
        }
    }

    fun getPhotoOutputOptions(context: Context): ImageCapture.OutputFileOptions {
        return ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            createContentValues("image/jpeg", "Pictures/$APP_FOLDER")
        ).build()
    }

    fun getVideoOutputOptions(context: Context): MediaStoreOutputOptions {
        return MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(createContentValues("video/mp4", "Movies/$APP_FOLDER"))
            .build()
    }
}