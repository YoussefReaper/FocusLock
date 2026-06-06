package com.focuslock.mdm

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LinkAdapter(
    private val links   : List<Constants.WebLink>,
    private val context : Context,
    private val onClick : (Constants.WebLink) -> Unit
) : RecyclerView.Adapter<LinkAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title    : TextView = view.findViewById(R.id.linkTitle)
        val timer    : TextView = view.findViewById(R.id.linkTimer)
        val url      : TextView = view.findViewById(R.id.linkUrl)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_link, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val link      = links[position]
        val theme     = UiPrefs.getTheme(context)

        holder.title.text = link.title
        holder.url.text   = link.url.removePrefix("https://").removePrefix("http://")
        holder.timer.text = if (link.category.isBlank()) "Custom" else link.category

        holder.title.setTextColor(theme.textPrimary)
        holder.url.setTextColor(theme.textSecondary)
        holder.timer.setTextColor(theme.textSecondary)
        holder.itemView.setBackgroundColor(theme.card)

        holder.itemView.alpha    = 1f
        holder.itemView.isEnabled = true
        holder.itemView.setOnClickListener { onClick(link) }
    }

    override fun getItemCount() = links.size
}