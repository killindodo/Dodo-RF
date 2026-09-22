package com.killindodo.dodo_rf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.killindodo.dodo_rf.databinding.ItemCapturedSignalBinding
import com.killindodo.dodo_rf.model.CapturedSignal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CapturedSignalAdapter(
    private val onSaveClick: (CapturedSignal) -> Unit
) : ListAdapter<CapturedSignal, CapturedSignalAdapter.ViewHolder>(DiffCallback) {

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCapturedSignalBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemCapturedSignalBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CapturedSignal) {
            binding.tvCode.text = item.code
            binding.tvFreq.text = String.format(Locale.US, "%.2f MHz", item.frequency)
            binding.tvBits.text = "${item.bits}-bit"
            binding.tvProtocol.text = "Prot: ${item.protocol}"
            if (item.pulse > 0) {
                binding.tvPulse.text = "${item.pulse}µs"
                binding.tvPulse.visibility = android.view.View.VISIBLE
            } else {
                binding.tvPulse.visibility = android.view.View.GONE
            }

            binding.tvTime.text = if (item.timestamp > 0) {
                // If timestamp looks like uptime ms or epoch ms
                timeFormat.format(Date(System.currentTimeMillis()))
            } else {
                "Just now"
            }

            binding.btnSave.setOnClickListener {
                onSaveClick(item)
            }

            binding.root.setOnClickListener {
                onSaveClick(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<CapturedSignal>() {
        override fun areItemsTheSame(oldItem: CapturedSignal, newItem: CapturedSignal): Boolean {
            return oldItem.code == newItem.code && oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: CapturedSignal, newItem: CapturedSignal): Boolean {
            return oldItem == newItem
        }
    }
}
