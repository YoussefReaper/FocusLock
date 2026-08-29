package com.focuslock.mdm

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * The video list.
 *
 * Three states, and the wording of each matters: a locked item says when it
 * opens rather than simply refusing, because the countdown is the reward
 * mechanic. Nothing here scolds, and nothing is permanently out of reach.
 */
class VideoAdapter(
    private val context: Context,
    private var items: List<VideoItem>,
    private var canUnlockNow: Boolean,
    private val onUnlock: (VideoItem) -> Unit,
    private val onPlay: (VideoItem) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tvVideoName)
        val badge: TextView = view.findViewById(R.id.tvVideoBadge)
        val subtitle: TextView = view.findViewById(R.id.tvVideoSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val tokens = UiPrefs.resolve(context)

        holder.itemView.background = FocusUi.roundedShape(
            context,
            tokens.surface,
            tokens.radiusDp,
            UiPrefs.blend(tokens.divider, tokens.surface, 0.2f)
        )

        holder.name.text = item.name
        holder.name.setTextColor(tokens.textPrimary)
        holder.name.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
        holder.name.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(15f))

        holder.subtitle.typeface = tokens.typeface
        holder.subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(12f))

        holder.badge.typeface = Typeface.create(tokens.typeface, Typeface.BOLD)
        holder.badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, tokens.scaled(12f))

        when {
            item.isUnlocked -> {
                holder.itemView.alpha = 1f
                holder.badge.text = "Play"
                holder.badge.setTextColor(tokens.onAccent)
                holder.badge.background = FocusUi.roundedShape(context, tokens.success, 999)
                holder.subtitle.text = "Yours to watch"
                holder.subtitle.setTextColor(tokens.success)
                holder.itemView.isClickable = true
                holder.itemView.setOnClickListener { onPlay(item) }
            }

            canUnlockNow -> {
                holder.itemView.alpha = 1f
                holder.badge.text = "Unlock"
                holder.badge.setTextColor(tokens.onAccent)
                holder.badge.background = FocusUi.roundedShape(context, tokens.accent, 999)
                holder.subtitle.text = "The unlock for today is waiting"
                holder.subtitle.setTextColor(tokens.accent)
                holder.itemView.isClickable = true
                holder.itemView.setOnClickListener { onUnlock(item) }
            }

            else -> {
                holder.itemView.alpha = 0.55f
                holder.badge.text = "Locked"
                holder.badge.setTextColor(tokens.textMuted)
                holder.badge.background = FocusUi.roundedShape(context, tokens.track, 999)
                holder.subtitle.text = "Opens in " + VideoManager.nextUnlockFormatted(context)
                holder.subtitle.setTextColor(tokens.textMuted)
                holder.itemView.isClickable = false
                holder.itemView.setOnClickListener(null)
            }
        }
    }

    fun update(newItems: List<VideoItem>, canUnlock: Boolean) {
        items = newItems
        canUnlockNow = canUnlock
        notifyDataSetChanged()
    }
}
