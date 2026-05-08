package com.melodix.player.service

import android.app.*
import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.melodix.player.R
import com.melodix.player.model.Song
import com.melodix.player.ui.MainActivity

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "melodix_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.melodix.player.PLAY"
        const val ACTION_PAUSE = "com.melodix.player.PAUSE"
        const val ACTION_NEXT = "com.melodix.player.NEXT"
        const val ACTION_PREVIOUS = "com.melodix.player.PREVIOUS"
        const val ACTION_STOP = "com.melodix.player.STOP"

        var isPlaying = false
        var currentPosition = 0
        var songs: ArrayList<Song> = arrayListOf()
        var repeatMode = 0
        var shuffleMode = false
    }

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    val binder = MusicBinder()

    // Handler that ticks every second to push position updates into the MediaSession
    // and refresh the notification progress bar while music is playing.
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying) {
                updatePlaybackState()          // push current position → seekbar animates
                updateNotification()           // refresh progress on the notification bar
            }
            progressHandler.postDelayed(this, 1000)
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    // ─────────────────────────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        mediaSession = MediaSessionCompat(this, "MelodixSession").apply {
            isActive = true
            // Allow seeking from the notification / lock screen
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onSeekTo(pos: Long) {
                    seekTo(pos.toInt())
                    updatePlaybackState()
                }
                override fun onPlay() { resumeMedia() }
                override fun onPause() { pauseMedia() }
                override fun onSkipToNext() { skipToNext() }
                override fun onSkipToPrevious() { skipToPrevious() }
            })
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY     -> resumeMedia()
            ACTION_PAUSE    -> pauseMedia()
            ACTION_NEXT     -> skipToNext()
            ACTION_PREVIOUS -> skipToPrevious()
            ACTION_STOP     -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacks(progressRunnable)
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession.release()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  Playback control
    // ─────────────────────────────────────────────────────────────

    fun initMediaPlayer() {
        if (songs.isEmpty()) return
        val song = songs[currentPosition]

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )

            setOnErrorListener { _, what, extra ->
                // Log the exact error codes so you can debug per-device
                Log.e("MusicService", "MediaPlayer error: what=$what extra=$extra")
                skipToNext()   // gracefully skip unplayable files
                true
            }

            setOnPreparedListener {
                updateMediaMetadata(song)
                playMedia()
            }

            setOnCompletionListener { onSongCompletion() }

            try {
                // Prefer URI over raw path — works on all Android versions
                // including scoped storage (Android 10+)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    song.id
                )
                setDataSource(applicationContext, uri)
                prepareAsync()   // non-blocking — onPreparedListener fires when ready
            } catch (e: Exception) {
                Log.e("MusicService", "setDataSource failed for: ${song.path}", e)
                skipToNext()
            }
        }
        requestAudioFocus()
    }

    // Remove playMedia() call from outside — it now lives in onPreparedListener
    fun skipToNext() {
        currentPosition = if (shuffleMode) (0 until songs.size).random()
        else (currentPosition + 1) % songs.size
        initMediaPlayer()   // playMedia() is called by onPreparedListener
    }
    fun playMedia() {
        if (requestAudioFocus()) {
            mediaPlayer?.start()
            isPlaying = true
            updatePlaybackState()
            updateNotification()
            startProgressUpdates()
        }
    }

    fun pauseMedia() {
        mediaPlayer?.pause()
        isPlaying = false
        stopProgressUpdates()
        updatePlaybackState()
        updateNotification()
    }

    fun resumeMedia() {
        mediaPlayer?.start()
        isPlaying = true
        updatePlaybackState()
        updateNotification()
        startProgressUpdates()
    }


    fun skipToPrevious() {
        if (getCurrentPosition() > 3000) {
            seekTo(0)
            updatePlaybackState()
        } else {
            currentPosition = if (currentPosition - 1 < 0) songs.size - 1
            else currentPosition - 1
            initMediaPlayer()
            playMedia()
        }
    }

    private fun onSongCompletion() {
        if (repeatMode == 2) { initMediaPlayer(); playMedia() } else skipToNext()
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int        = mediaPlayer?.duration ?: 0
    fun isMediaPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    // ─────────────────────────────────────────────────────────────
    //  MediaSession metadata & state  ← drives the seekbar
    // ─────────────────────────────────────────────────────────────

    /**
     * Push song title / artist / duration into the MediaSession.
     * The system uses METADATA_KEY_DURATION to set the seekbar maximum.
     */
    private fun updateMediaMetadata(song: Song) {
        val albumArt: Bitmap? = try {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                songs[currentPosition].albumId          // use albumId, not song.id
            )
            contentResolver.openInputStream(albumArtUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.w("MusicService", "Album art not found, using default", e)
            null
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE,  song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM,  song.album)
            // DURATION is mandatory — without it the seekbar won't appear
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .apply { albumArt?.let { putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) } }
            .build()

        mediaSession.setMetadata(metadata)
    }

    /**
     * Push current playback position + speed into the MediaSession.
     * Android uses PLAYBACK_POSITION and PLAYBACK_SPEED to animate the seekbar
     * in real time without needing a constant stream of updates.
     *
     * Call this:
     *  • on play / pause / seek
     *  • every ~1 s from progressRunnable (keeps the timestamp text in sync)
     */
    private fun updatePlaybackState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
        else           PlaybackStateCompat.STATE_PAUSED

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY              or
                        PlaybackStateCompat.ACTION_PAUSE             or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT      or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS  or
                        PlaybackStateCompat.ACTION_SEEK_TO           // enables seek from notification
            )
            // position + playback speed  → system animates seekbar thumb in real time
            .setState(
                state,
                getCurrentPosition().toLong(),
                if (isPlaying) 1f else 0f   // speed 1.0 = normal; 0.0 = paused
            )
            .build()

        mediaSession.setPlaybackState(playbackState)
    }

    // ─────────────────────────────────────────────────────────────
    //  Notification
    // ─────────────────────────────────────────────────────────────

    fun updateNotification() {
        if (songs.isEmpty()) return
        val song     = songs[currentPosition]
        val duration = getDuration().takeIf { it > 0 } ?: song.duration.toInt()
        val position = getCurrentPosition()

        val mainIntent = Intent(this, MainActivity::class.java)
            .apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingMain = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        fun serviceIntent(action: String) = PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, MusicService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val albumArt: Bitmap? = try {
            val albumArtUri = ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                songs[currentPosition].albumId          // use albumId, not song.id
            )
            contentResolver.openInputStream(albumArtUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            Log.w("MusicService", "Album art not found, using default", e)
            null
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            // subText shows "1:23 / 3:45" below the artist name
            .setSubText("${formatMs(position)} / ${formatMs(duration)}")
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(
                albumArt ?: BitmapFactory.decodeResource(resources, R.drawable.ic_default_album)
            )
            .setContentIntent(pendingMain)
            // Progress bar: max = duration ms, progress = current ms, indeterminate = false
            .setProgress(duration, position, false)
            .addAction(R.drawable.ic_skip_previous, "Previous", serviceIntent(ACTION_PREVIOUS))
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause"   else "Play",
                serviceIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
            )
            .addAction(R.drawable.ic_skip_next, "Next", serviceIntent(ACTION_NEXT))
            .setStyle(
                // MediaStyle binds the notification to the MediaSession.
                // Android 13+ uses the session's PlaybackState to render
                // an interactive seekbar automatically.
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            // showWhen + usesChronometer = elapsed-time clock as a fallback on older ROMs
            .setShowWhen(true)
            .setUsesChronometer(false)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ─────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────

    private fun startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.postDelayed(progressRunnable, 1000)
    }

    private fun stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable)
    }

    /** "123456 ms  →  2:03" */
    private fun formatMs(ms: Int): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    private fun requestAudioFocus(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS          -> pauseMedia()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseMedia()
                        AudioManager.AUDIOFOCUS_GAIN          -> resumeMedia()
                    }
                }
                .build()
            audioFocusRequest = req
            return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
        return true
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Melodix Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

}