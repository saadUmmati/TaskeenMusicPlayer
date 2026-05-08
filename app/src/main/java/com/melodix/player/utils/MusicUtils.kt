package com.melodix.player.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.melodix.player.model.Song
import java.io.File

object MusicUtils {

    fun getAllSongs(context: Context): ArrayList<Song> {
        val tempList = ArrayList<Song>()
        val songIds = HashSet<Long>() // Duplicate IDs track karne ke liye

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            // ... baki columns ke index ...

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)

                if (!songIds.contains(id)) {
                    // ERROR FIX: Pehle cursor se data nikaal kar variables mein save karein
                    val title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                    val artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                    val album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM))
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                    val path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    val albumId = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))

                    // Ab ye variables use karein
                    val song = Song(id, title, artist, album, duration, path, albumId)
                    tempList.add(song)
                    songIds.add(id)
                }
            }
        }
        return tempList
    }

    fun searchSongs(allSongs: List<Song>, query: String): List<Song> {
        if (query.isBlank()) return allSongs
        val q = query.lowercase()
        return allSongs.filter {
            it.title.lowercase().contains(q) ||
            it.artist.lowercase().contains(q) ||
            it.album.lowercase().contains(q)
        }
    }

    fun sortSongs(songs: List<Song>, sortBy: SortOption): List<Song> {
        return when (sortBy) {
            SortOption.TITLE -> songs.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> songs.sortedBy { it.artist.lowercase() }
            SortOption.ALBUM -> songs.sortedBy { it.album.lowercase() }
            SortOption.DURATION -> songs.sortedByDescending { it.duration }
            SortOption.RECENTLY_ADDED -> songs.sortedByDescending { it.dateAdded }
        }
    }

    enum class SortOption { TITLE, ARTIST, ALBUM, DURATION, RECENTLY_ADDED }
}
