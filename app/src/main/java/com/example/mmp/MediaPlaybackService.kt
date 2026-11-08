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
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

object PlayerBridge {
    var onPlayPause: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var currentTitle: String = "MMP"
    var currentArtist: String = "Готов к воспроизведению"
    var currentBitmap: Bitmap? = null
    var isPlaying: Boolean = false
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

                if (PlayerBridge.currentBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_cover, PlayerBridge.currentBitmap)
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

    override fun onCreate() {
        super.onCreate()
        createChannel()
        mediaSession = MediaSessionCompat(this, "MMPMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    // Вызываем только если действие инициировано извне (наушники/BT)
                    if (!PlayerBridge.isPlaying) {
                        PlayerBridge.onPlayPause?.invoke()
                    }
                }
                override fun onPause() {
                    if (PlayerBridge.isPlaying) {
                        PlayerBridge.onPlayPause?.invoke()
                    }
                }
                override fun onSkipToNext() { PlayerBridge.onNext?.invoke() }
                override fun onSkipToPrevious() { PlayerBridge.onPrev?.invoke() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_TOGGLE" -> PlayerBridge.onPlayPause?.invoke()
            "ACTION_NEXT" -> PlayerBridge.onNext?.invoke()
            "ACTION_PREV" -> PlayerBridge.onPrev?.invoke()
            "ACTION_UPDATE" -> { /* Просто обновляем статус и нотификацию */ }
        }
        updateNotification()
        MMPWidgetProvider.updateAllWidgets(this)
        return START_STICKY
    }

    private fun updateNotification() {
        val state = if (PlayerBridge.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build()
        )

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, PlayerBridge.currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, PlayerBridge.currentArtist)
        if (PlayerBridge.currentBitmap != null) {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, PlayerBridge.currentBitmap)
        }
        mediaSession.setMetadata(metaBuilder.build())

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

        val playPauseIcon = if (PlayerBridge.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(PlayerBridge.currentTitle)
            .setContentText(PlayerBridge.currentArtist)
            .setLargeIcon(PlayerBridge.currentBitmap)
            .setContentIntent(openAppIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(PlayerBridge.isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Prev", prevPending)
            .addAction(playPauseIcon, "Play/Pause", togglePending)
            .addAction(android.R.drawable.ic_media_next, "Next", nextPending)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIF_ID, notification)
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
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
