package com.chatapp.ui.contacts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.chatapp.R
import com.chatapp.data.User
import com.chatapp.databinding.ItemContactBinding

class ContactsAdapter(
    private val onClick: (User) -> Unit
) : RecyclerView.Adapter<ContactsAdapter.ContactViewHolder>() {

    private val items = mutableListOf<User>()

    fun submit(newItems: List<User>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun updatePreview(peerId: String, preview: String, time: String) {
        val index = items.indexOfFirst { it.id == peerId }
        if (index >= 0) {
            items[index] = items[index].copy(
                email = preview,
                lastSeen = time
            )
            notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ContactViewHolder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.userName.text = user.name
            binding.userStatus.text = if (user.online) "Online" else "Offline"
            binding.onlineDot.visibility = if (user.online) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }
            val hasAvatar = !user.avatar.isNullOrBlank()
            binding.avatarImage.visibility = if (hasAvatar) View.VISIBLE else View.GONE
            binding.avatar.visibility = if (hasAvatar) View.GONE else View.VISIBLE
            if (hasAvatar) {
                binding.avatarImage.load(user.avatar) {
                    placeholder(R.drawable.avatar_bg)
                    error(R.drawable.avatar_bg)
                    crossfade(true)
                    transformations(CircleCropTransformation())
                }
            } else {
                binding.avatar.text = user.name.take(1).uppercase()
            }
            binding.root.setOnClickListener { onClick(user) }
        }
    }
}
