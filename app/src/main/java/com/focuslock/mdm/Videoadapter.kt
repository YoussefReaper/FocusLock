package com.focuslock.mdm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VideoAdapter(
    private val context : Context,
    private var items   : List<VideoItem>,
    private val canUnlockNow: Boolean,
    private val onUnlock: (VideoItem) -> Unit,
    private val onPlay  : (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName     : TextView = view.findViewById(R.id.tvVideoName)
        val tvBadge    : TextView = view.findViewById(R.id.tvVideoBadge)
        val tvSubtitle : TextView = view.findViewById(R.id.tvVideoSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val theme = UiPrefs.getTheme(context)

        holder.itemView.setBackgroundColor(theme.card)
        holder.tvName.text = item.name
        holder.tvName.setTextColor(theme.textPrimary)

        when {
            // ── Unlocked: tap to play ──────────────────────────
            item.isUnlocked -> {
                holder.itemView.alpha = 1f
                holder.tvBadge.text       = "▶  Play"
                holder.tvBadge.setBackgroundResource(R.drawable.badge_play)
                holder.tvSubtitle.text    = "Unlocked"
                holder.tvSubtitle.setTextColor(0xFF4CAF50.toInt())
                holder.itemView.setOnClickListener { onPlay(item) }
            }

            // ── Locked but unlock slot available ──────────────
            canUnlockNow -> {
                holder.itemView.alpha = 0.85f
                holder.tvBadge.text       = "🔓  Unlock"
                holder.tvBadge.setBackgroundResource(R.drawable.badge_unlock)
                holder.tvSubtitle.text    = "Tap to use today's unlock"
                holder.tvSubtitle.setTextColor(0xFF3A7BFF.toInt())
                holder.itemView.setOnClickListener { onUnlock(item) }
            }

            // ── Locked, no unlock available ───────────────────
            else -> {
                holder.itemView.alpha = 0.35f
                holder.tvBadge.text       = "🔒  Locked"
                holder.tvBadge.setBackgroundResource(R.drawable.badge_locked)
                holder.tvSubtitle.text    = "Next unlock: ${VideoManager.nextUnlockFormatted(context)}"
                holder.tvSubtitle.setTextColor(0xFF666666.toInt())
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    fun update(newItems: List<VideoItem>, canUnlock: Boolean) {
        // canUnlockNow is a val so we recreate — adapter is cheap
        items = newItems
        notifyDataSetChanged()
    }
}