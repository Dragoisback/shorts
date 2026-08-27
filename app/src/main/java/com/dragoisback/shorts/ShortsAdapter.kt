package com.dragoisback.shorts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView

/**
 * One full-screen page per video. Only the page that is currently on screen owns the shared
 * ExoPlayer instance; every other page just shows its placeholder background.
 */
class ShortsAdapter(
    private val items: MutableList<VideoItem>,
    private val onTap: () -> Unit
) : RecyclerView.Adapter<ShortsAdapter.PageHolder>() {

    class PageHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerView: PlayerView = view.findViewById(R.id.player_view)
        val title: TextView = view.findViewById(R.id.video_title)
        val playIcon: ImageView = view.findViewById(R.id.play_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_short, parent, false)
        return PageHolder(view)
    }

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.playIcon.visibility = View.GONE
        holder.playerView.setOnClickListener { onTap() }
        holder.itemView.setOnClickListener { onTap() }
    }

    override fun onViewRecycled(holder: PageHolder) {
        super.onViewRecycled(holder)
        holder.playerView.player = null
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newItems: List<VideoItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): VideoItem? = items.getOrNull(position)
}
