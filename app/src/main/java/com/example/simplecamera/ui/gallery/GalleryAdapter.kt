package com.example.simplecamera.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.simplecamera.data.model.MediaFile
import com.example.simplecamera.databinding.ItemGalleryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GalleryAdapter(
    private val onClick: (MediaFile) -> Unit
) : ListAdapter<MediaFile, GalleryAdapter.ViewHolder>(MediaDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy\nHH:mm", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemGalleryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MediaFile) {
            Glide.with(binding.root)
                .load(item.uri)
                .centerCrop()
                .into(binding.thumbnail)

            binding.typeIcon.visibility = if (item.isVideo) View.VISIBLE else View.GONE
            binding.dateText.text = dateFormat.format(Date(item.dateAdded))

            binding.root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
        override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile): Boolean = oldItem == newItem
    }
}