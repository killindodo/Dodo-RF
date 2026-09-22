package com.killindodo.dodo_rf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.killindodo.dodo_rf.databinding.ItemStoredSignalBinding
import com.killindodo.dodo_rf.model.StoredSignal
import java.util.Locale

class StoredSignalAdapter(
    private val onReplayClick: (StoredSignal) -> Unit,
    private val onDeleteClick: (StoredSignal) -> Unit
) : ListAdapter<StoredSignal, StoredSignalAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemStoredSignalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemStoredSignalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: StoredSignal) {
            binding.tvSignalName.text = item.name
            binding.tvCode.text = item.code
            binding.tvFreq.text = String.format(Locale.US, "%.2f MHz", item.freq)

            binding.btnReplay.setOnClickListener {
                onReplayClick(item)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<StoredSignal>() {
        override fun areItemsTheSame(oldItem: StoredSignal, newItem: StoredSignal): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: StoredSignal, newItem: StoredSignal): Boolean {
            return oldItem == newItem
        }
    }
}
