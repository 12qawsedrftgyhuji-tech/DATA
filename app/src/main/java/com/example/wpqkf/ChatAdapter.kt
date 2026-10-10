package com.example.wpqkf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wpqkf.databinding.ItemMessageAiBinding
import com.example.wpqkf.databinding.ItemMessageUserBinding

class ChatAdapter(
    private val onAiMessageClick: (String) -> Unit
) : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(Diff) {

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_AI = 1
    }

    class UserVH(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root)
    class AiVH(val binding: ItemMessageAiBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int) =
        if (getItem(position).isUser) TYPE_USER else TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserVH(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            AiVH(ItemMessageAiBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is UserVH -> holder.binding.tvMessage.text = item.text
            is AiVH -> {
                holder.binding.tvMessage.text = item.text
                holder.binding.tvMessage.setOnClickListener { onAiMessageClick(item.text) }
            }
        }
    }

    object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
        override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
    }
}