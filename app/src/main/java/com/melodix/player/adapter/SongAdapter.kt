package com.melodix.player.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.melodix.player.R
import com.melodix.player.model.Song
import com.melodix.player.service.MusicService
import com.melodix.player.loadAlbumArt

class SongAdapter(
    private val context: Context,
    private val songs: ArrayList<Song>,
    private val onSongClick: (Int) -> Unit,
    private val onSongLongClick: (Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var currentPlayingPos = -1

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: CardView = itemView.findViewById(R.id.cardSong)
        val albumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        val title: TextView = itemView.findViewById(R.id.tvTitle)
        val artist: TextView = itemView.findViewById(R.id.tvArtist)
        val duration: TextView = itemView.findViewById(R.id.tvDuration)
        val nowPlaying: ImageView = itemView.findViewById(R.id.ivNowPlaying)
        val menu: ImageView = itemView.findViewById(R.id.ivMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.title.text = song.title
        holder.artist.text = song.artist
        holder.duration.text = song.durationFormatted

        holder.albumArt.loadAlbumArt(context, song)


        val isCurrentSong = position == MusicService.currentPosition && MusicService.songs.isNotEmpty()
        holder.nowPlaying.visibility = if (isCurrentSong) View.VISIBLE else View.GONE
        holder.card.setCardBackgroundColor(
            context.getColor(if (isCurrentSong) R.color.surface_highlight else R.color.surface)
        )

        holder.card.setOnClickListener {
            onSongClick(position)
        }

        holder.card.setOnLongClickListener {
            onSongLongClick(song)
            true
        }

        holder.menu.setOnClickListener {
            onSongLongClick(song)
        }
    }

    override fun getItemCount(): Int = songs.size

    fun updateCurrentPlaying(position: Int) {
        val oldPos = currentPlayingPos
        currentPlayingPos = position
        if (oldPos != -1) notifyItemChanged(oldPos)
        notifyItemChanged(position)
    }

    fun updateSongs(newSongs: List<Song>) {
        songs.clear()
        songs.addAll(newSongs)
        notifyDataSetChanged()
    }
}
