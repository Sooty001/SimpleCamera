package com.example.simplecamera.data.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class MediaFile(
    val id: Long,
    val uri: Uri,
    val isVideo: Boolean,
    val dateAdded: Long
) : Parcelable