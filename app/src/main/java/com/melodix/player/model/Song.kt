package com.melodix.player.model

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.Serializable

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumId: Long,
    val dateAdded: Long = 0L
    // Removed albumArtUri from constructor since you are calculating it via function
) : Serializable {

    fun getAlbumArtUri(context: Context): Uri {
        // Strategy 1: MediaStore embedded art URI (Android 9 and below)
        val legacyUri = Uri.parse("content://media/external/audio/albumart/$albumId")

        // Strategy 2: Scoped storage safe URI (Android 10+)
        val modernUri = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) modernUri else legacyUri
    }

    val durationFormatted: String
        get() {
            val minutes = (duration / 1000) / 60
            val seconds = (duration / 1000) % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}