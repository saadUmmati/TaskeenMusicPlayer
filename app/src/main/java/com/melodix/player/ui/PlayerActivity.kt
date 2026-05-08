package com.melodix.player.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.palette.graphics.Palette
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.melodix.player.R
import com.melodix.player.databinding.ActivityPlayerBinding
import com.melodix.player.service.MusicService
import android.graphics.drawable.Drawable
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import java.util.Locale



class PlayerActivity : AppCompatActivity(), ServiceConnection {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        setupControls()
        bindMusicService()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, this, Context.BIND_AUTO_CREATE)
    }

    private fun setupControls() {
        binding.btnPlayPause.setOnClickListener {
            musicService?.let {
                if (MusicService.isPlaying) it.pauseMedia() else it.resumeMedia()
                updatePlayPauseButton()
            }
        }

        binding.btnNext.setOnClickListener {
            musicService?.skipToNext()
            updateUI()
        }

        binding.btnPrevious.setOnClickListener {
            musicService?.skipToPrevious()
            updateUI()
        }

        binding.btnShuffle.setOnClickListener {
            MusicService.shuffleMode = !MusicService.shuffleMode
            binding.btnShuffle.alpha = if (MusicService.shuffleMode) 1.0f else 0.4f
            val msg = if (MusicService.shuffleMode) "Shuffle On" else "Shuffle Off"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        binding.btnRepeat.setOnClickListener {
            MusicService.repeatMode = (MusicService.repeatMode + 1) % 3
            when (MusicService.repeatMode) {
                0 -> {
                    binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                    binding.btnRepeat.alpha = 0.4f
                    Toast.makeText(this, "Repeat Off", Toast.LENGTH_SHORT).show()
                }
                1 -> {
                    binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                    binding.btnRepeat.alpha = 1.0f
                    Toast.makeText(this, "Repeat All", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                    binding.btnRepeat.alpha = 1.0f
                    Toast.makeText(this, "Repeat One", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.seekTo(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startProgressUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                musicService?.let { service ->
                    val current = service.getCurrentPosition()
                    val duration = service.getDuration()
                    if (duration > 0) {
                        binding.seekBar.max = duration
                        binding.seekBar.progress = current
                        binding.tvCurrentTime.text = formatTime(current)
                        binding.tvTotalTime.text = formatTime(duration)
                    }
                    updatePlayPauseButton()
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(updateRunnable!!)
    }


    private fun updateUI() {
        if (MusicService.songs.isEmpty() ||
            MusicService.currentPosition >= MusicService.songs.size) return

        val song = MusicService.songs[MusicService.currentPosition]

        binding.tvSongTitle.text = song.title
        binding.tvArtistName.text = song.artist
        binding.tvAlbumName.text = song.album

        // Fix 2, 3, 4: correct Glide chain — no RequestOptions, size in constructor
        Glide.with(this)
            .asBitmap()
            .load(song.getAlbumArtUri(this)) // song.albumArtUri ko song.getAlbumArtUri(this) se replace karein
            .placeholder(R.drawable.ic_default_album)
            .error(R.drawable.ic_default_album)
            .transform(RoundedCorners(32))
            .into(object : CustomTarget<Bitmap>(           // Fix 4: size via constructor, not override
                Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL
            ) {
                override fun onResourceReady(
                    resource: Bitmap,                      // Fix 5: no redundant qualifiers
                    transition: Transition<in Bitmap>?
                ) {
                    if (isDestroyed || isFinishing) return
                    binding.ivAlbumArt.setImageBitmap(resource)
                    Palette.from(resource).generate { palette ->
                        val color = palette?.darkVibrantSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb
                            ?: getColor(R.color.background)
                        binding.root.setBackgroundColor(color)
                    }
                }

                override fun onLoadCleared(placeholder: Drawable?) {   // Fix 5
                    if (isDestroyed || isFinishing) return
                    binding.ivAlbumArt.setImageResource(R.drawable.ic_default_album)
                }
            })

        updatePlayPauseButton()
        binding.btnShuffle.alpha = if (MusicService.shuffleMode) 1.0f else 0.4f
        binding.btnRepeat.setImageResource(
            if (MusicService.repeatMode == 2) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        binding.btnRepeat.alpha = if (MusicService.repeatMode > 0) 1.0f else 0.4f
    }


    private fun updatePlayPauseButton() {
        binding.btnPlayPause.setImageResource(
            if (MusicService.isPlaying) R.drawable.ic_pause_circle else R.drawable.ic_play_circle
        )
    }

    private fun formatTime(ms: Int): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format(Locale.US, "%d:%02d", minutes, secs)
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as MusicService.MusicBinder
        musicService = binder.getService()
        isBound = true
        updateUI()
        startProgressUpdate()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        isBound = false
        musicService = null
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
        if (isBound) {
            unbindService(this)
            isBound = false
        }
    }
}
