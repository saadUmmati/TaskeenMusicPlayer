package com.melodix.player.ui

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.melodix.player.R
import com.melodix.player.adapter.SongAdapter
import com.melodix.player.databinding.ActivityMainBinding
import com.melodix.player.model.Song
import com.melodix.player.service.MusicService
import com.melodix.player.utils.MusicUtils
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var binding: ActivityMainBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private var allSongs = arrayListOf<Song>()
    private lateinit var adapter: SongAdapter
    private var currentSort = MusicUtils.SortOption.TITLE

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) loadMusic()
        else showPermissionRationale()
    }

    private val playerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateMiniPlayer()
        adapter.updateCurrentPlaying(MusicService.currentPosition)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupRecyclerView()
        setupMiniPlayer()
        checkPermissions()
    }

    private fun setupRecyclerView() {
        adapter = SongAdapter(
            this, allSongs,
            onSongClick = { position -> playSong(position) },
            onSongLongClick = { song -> showSongOptions(song) }
        )
        binding.rvSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupMiniPlayer() {
        binding.miniPlayer.setOnClickListener {
            if (MusicService.songs.isNotEmpty()) openPlayer()
        }
        binding.btnMiniPlayPause.setOnClickListener {
            musicService?.let {
                if (MusicService.isPlaying) it.pauseMedia() else it.resumeMedia()
                updateMiniPlayer()
            }
        }
        binding.btnMiniNext.setOnClickListener {
            musicService?.skipToNext()
            updateMiniPlayer()
            adapter.updateCurrentPlaying(MusicService.currentPosition)
        }
    }

    private fun checkPermissions() {
        val permsNeeded = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) permsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) permsNeeded.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) permsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permsNeeded.isEmpty()) loadMusic()
        else permissionLauncher.launch(permsNeeded.toTypedArray())
    }

    private fun loadMusic() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        thread {
            val songs = MusicUtils.getAllSongs(this)
            val sorted = MusicUtils.sortSongs(songs, currentSort)
            runOnUiThread {
                allSongs.clear()
                allSongs.addAll(sorted)
                adapter.notifyDataSetChanged()
                binding.progressBar.visibility = View.GONE
                if (allSongs.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvSongCount.text = "0 songs"
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.tvSongCount.text = "${allSongs.size} songs"
                }
                updateMiniPlayer()
            }
        }
    }

    private fun playSong(position: Int) {
        MusicService.songs = allSongs
        MusicService.currentPosition = position
        if (!isBound) {
            Intent(this, MusicService::class.java).also {
                startForegroundService(it)
                bindService(it, this, Context.BIND_AUTO_CREATE)
            }
        } else {
            musicService?.initMediaPlayer()
            musicService?.playMedia()
            adapter.updateCurrentPlaying(position)
            updateMiniPlayer()
        }
        openPlayer()
    }

    private fun openPlayer() {
        val intent = Intent(this, PlayerActivity::class.java)
        playerLauncher.launch(intent)
    }

    private fun updateMiniPlayer() {
        if (MusicService.songs.isEmpty() || MusicService.currentPosition >= MusicService.songs.size) {
            binding.miniPlayer.visibility = View.GONE
            return
        }
        val song = MusicService.songs[MusicService.currentPosition]
        binding.miniPlayer.visibility = View.VISIBLE
        binding.tvMiniTitle.text = song.title
        binding.tvMiniArtist.text = song.artist
        binding.btnMiniPlayPause.setImageResource(
            if (MusicService.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        Glide.with(this)
            .load(song.getAlbumArtUri(this)) // Function call karein aur 'this' (context) pass karein
            .placeholder(R.drawable.ic_default_album)
            .error(R.drawable.ic_default_album)
            .centerCrop()
            .into(binding.ivMiniAlbumArt)
    }

    private fun showSongOptions(song: Song) {
        val options = arrayOf("Play Next", "Add to Queue", "Song Info")
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle(song.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Will play next", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Added to queue", Toast.LENGTH_SHORT).show()
                    2 -> showSongInfo(song)
                }
            }
            .show()
    }

    private fun showSongInfo(song: Song) {
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Song Info")
            .setMessage(
                "Title: ${song.title}\n\n" +
                "Artist: ${song.artist}\n\n" +
                "Album: ${song.album}\n\n" +
                "Duration: ${song.durationFormatted}\n\n" +
                "Path: ${song.path}"
            )
            .setPositiveButton("Roger", null)
            .show()
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Permission Required")
            .setMessage("Storage permission is needed to read your music files.")
            .setPositiveButton("Grant") { _, _ -> checkPermissions() }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Search songs..."
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val filtered = MusicUtils.searchSongs(allSongs, newText ?: "")
                adapter.updateSongs(filtered)
                binding.tvSongCount.text = "${filtered.size} songs"
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val options = arrayOf("Title", "Artist", "Album", "Duration", "Recently Added")
        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Sort by")
            .setItems(options) { _, which ->
                currentSort = MusicUtils.SortOption.values()[which]
                val sorted = MusicUtils.sortSongs(allSongs, currentSort)
                adapter.updateSongs(sorted)
            }
            .show()
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as MusicService.MusicBinder
        musicService = binder.getService()
        isBound = true
        musicService?.initMediaPlayer()
        musicService?.playMedia()
        adapter.updateCurrentPlaying(MusicService.currentPosition)
        updateMiniPlayer()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        isBound = false
        musicService = null
    }

    override fun onResume() {
        super.onResume()
        updateMiniPlayer()
        if (MusicService.songs.isNotEmpty())
            adapter.updateCurrentPlaying(MusicService.currentPosition)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(this)
            isBound = false
        }
    }
}
