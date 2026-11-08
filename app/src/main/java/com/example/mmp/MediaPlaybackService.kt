package com.example.mmp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

object PlayerBridge {
    var onPlay: (() -> Unit)? = null
    var onPause: (() -> Unit)? = null
    var onToggle: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onSeekTo: ((Long) -> Unit)? = null
    var onToggleShuffle: (() -> Unit)? = null
    var onCycleRepeat: (() -> Unit)? = null

    var currentTitle: String = "MMP"
    var currentArtist: String = "Готов к воспроизведению"
    var currentBitmap: Bitmap? = null
    var isPlaying: Boolean = false
    var currentPositionMs: Long = 0L
    var durationMs: Long = 0L
    var isShuffleEnabled: Boolean = false
    var repeatModeOrdinal: Int = 0
}

class MMPWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAllWidgets(context)
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, MMPWidgetProvider::class.java))
                if (ids.isEmpty()) return

                val views = RemoteViews(context.packageName, R.layout.mmp_widget)
                views.setTextViewText(R.id.widget_title, PlayerBridge.currentTitle)
                views.setTextViewText(R.id.widget_artist, PlayerBridge.currentArtist)

                val bmp = PlayerBridge.currentBitmap?.takeIf { !it.isRecycled }
                if (bmp != null) {
                    views.setImageViewBitmap(R.id.widget_cover, bmp)
                } else {
                    views.setImageViewResource(R.id.widget_cover, android.R.drawable.ic_media_play)
                }

                val playIcon = if (PlayerBridge.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                views.setImageViewResource(R.id.widget_play_pause, playIcon)

                val openAppIntent = PendingIntent.getActivity(
                    context, 10, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val prevPending = PendingIntent.getService(
                    context, 11, Intent(context, MediaPlaybackService::class.java).setAction("ACTION_PREV"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val togglePending = PendingIntent.getService(
                    context, 12, Intent(context, MediaPlaybackService::class.java).setAction("ACTION_TOGGLE"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val nextPending = PendingIntent.getService(
                    context, 13, Intent(context, MediaPlaybackService::class.java).setAction("ACTION_NEXT"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)
                views.setOnClickPendingIntent(R.id.widget_prev, prevPending)
                views.setOnClickPendingIntent(R.id.widget_play_pause, togglePending)
                views.setOnClickPendingIntent(R.id.widget_next, nextPending)

                manager.updateAppWidget(ids, views)
            } catch (_: Throwable) {}
        }
    }
}

class MediaPlaybackService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private val CHANNEL_ID = "mmp_playback_channel"
    private val NOTIF_ID = 777
    private var isUpdatingInternally = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "MMPMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (!isUpdatingInternally) PlayerBridge.onPlay?.invoke()
                }
                override fun onPause() {
                    if (!isUpdatingInternally) PlayerBridge.onPause?.invoke()
                }
                override fun onSkipToNext() {
                    if (!isUpdatingInternally) PlayerBridge.onNext?.invoke()
                }
                override fun onSkipToPrevious() {
                    if (!isUpdatingInternally) PlayerBridge.onPrev?.invoke()
                }
                override fun onSeekTo(pos: Long) {
                    if (!isUpdatingInternally) PlayerBridge.onSeekTo?.invoke(pos)
                }
                override fun onSetShuffleMode(shuffleMode: Int) {
                    if (!isUpdatingInternally) PlayerBridge.onToggleShuffle?.invoke()
                }
                override fun onSetRepeatMode(repeatMode: Int) {
                    if (!isUpdatingInternally) PlayerBridge.onCycleRepeat?.invoke()
                }
                override fun onCustomAction(action: String?, extras: Bundle?) {
                    when (action) {
                        "ACTION_SHUFFLE" -> PlayerBridge.onToggleShuffle?.invoke()
                        "ACTION_REPEAT" -> PlayerBridge.onCycleRepeat?.invoke()
                    }
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_TOGGLE" -> PlayerBridge.onToggle?.invoke()
            "ACTION_NEXT" -> PlayerBridge.onNext?.invoke()
            "ACTION_PREV" -> PlayerBridge.onPrev?.invoke()
            "ACTION_SHUFFLE" -> PlayerBridge.onToggleShuffle?.invoke()
            "ACTION_REPEAT" -> PlayerBridge.onCycleRepeat?.invoke()
            "ACTION_UPDATE" -> { /* Просто обновляем нотификацию */ }
        }
        updateNotification()
        MMPWidgetProvider.updateAllWidgets(this)
        return START_STICKY
    }

    private fun updateNotification() {
        try {
            isUpdatingInternally = true
            val state = if (PlayerBridge.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            val playbackSpeed = if (PlayerBridge.isPlaying) 1f else 0f

            val playbackActions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE

            val safePos = PlayerBridge.currentPositionMs.coerceAtLeast(0L)
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(playbackActions)
                .setState(state, safePos, playbackSpeed)

            stateBuilder.addCustomAction("ACTION_SHUFFLE", "Перемешать", android.R.drawable.ic_menu_rotate)
            stateBuilder.addCustomAction("ACTION_REPEAT", "Повтор", android.R.drawable.ic_menu_revert)

            mediaSession.setPlaybackState(stateBuilder.build())

            val safeBitmap = PlayerBridge.currentBitmap?.takeIf { !it.isRecycled }
            val metaBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, PlayerBridge.currentTitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, PlayerBridge.currentArtist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, PlayerBridge.durationMs.coerceAtLeast(0L))
            if (safeBitmap != null) {
                metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, safeBitmap)
            }
            mediaSession.setMetadata(metaBuilder.build())
            mediaSession.setShuffleMode(if (PlayerBridge.isShuffleEnabled) PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
            mediaSession.setRepeatMode(
                when (PlayerBridge.repeatModeOrdinal) {
                    1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                    2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
            )
        } catch (_: Throwable) {
        } finally {
            isUpdatingInternally = false
        }

        val openAppIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPending = PendingIntent.getService(
            this, 1, Intent(this, MediaPlaybackService::class.java).setAction("ACTION_PREV"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val togglePending = PendingIntent.getService(
            this, 2, Intent(this, MediaPlaybackService::class.java).setAction("ACTION_TOGGLE"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val nextPending = PendingIntent.getService(
            this, 3, Intent(this, MediaPlaybackService::class.java).setAction("ACTION_NEXT"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val shufflePending = PendingIntent.getService(
            this, 4, Intent(this, MediaPlaybackService::class.java).setAction("ACTION_SHUFFLE"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val repeatPending = PendingIntent.getService(
            this, 5, Intent(this, MediaPlaybackService::class.java).setAction("ACTION_REPEAT"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIcon = if (PlayerBridge.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val safeBitmap = PlayerBridge.currentBitmap?.takeIf { !it.isRecycled }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(PlayerBridge.currentTitle)
            .setContentText(PlayerBridge.currentArtist)
            .setLargeIcon(safeBitmap)
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(PlayerBridge.isPlaying)
            .addAction(android.R.drawable.ic_menu_rotate, "Shuffle", shufflePending)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPending)
            .addAction(playPauseIcon, "Play/Pause", togglePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .addAction(android.R.drawable.ic_menu_revert, "Repeat", repeatPending)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(1, 2, 3)
            )
            .build()

        try {
            startForeground(NOTIF_ID, notification)
        } catch (_: Throwable) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MMP Music Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try { mediaSession.release() } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
