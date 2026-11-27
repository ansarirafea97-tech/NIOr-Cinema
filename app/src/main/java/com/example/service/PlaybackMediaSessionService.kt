package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.R

@OptIn(UnstableApi::class)
class PlaybackMediaSessionService : MediaSessionService() {

    companion object {
        const val CHANNEL_ID = "infuse_playback_channel"
        const val CHANNEL_NAME = "Media Playback Controls"
        const val NOTIFICATION_ID = 1001

        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        var activeMediaSession: MediaSession? = null

        @Volatile
        var instance: PlaybackMediaSessionService? = null
            private set

        fun registerSession(context: Context, session: MediaSession) {
            activeMediaSession = session
            runOnMainThread {
                val service = instance
                if (service != null) {
                    service.ensureSessionAdded(session)
                } else {
                    startService(context)
                }
            }
        }

        fun unregisterSession(context: Context, session: MediaSession?) {
            if (activeMediaSession === session) {
                activeMediaSession = null
            }
            runOnMainThread {
                val service = instance
                if (service != null && session != null) {
                    try {
                        if (service.sessions.contains(session)) {
                            service.removeSession(session)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                stopService(context)
            }
        }

        fun startService(context: Context) {
            try {
                val appContext = context.applicationContext
                val intent = Intent(appContext, PlaybackMediaSessionService::class.java)
                appContext.startService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val appContext = context.applicationContext
                val intent = Intent(appContext, PlaybackMediaSessionService::class.java)
                appContext.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun runOnMainThread(block: () -> Unit) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                mainHandler.post(block)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        configureNotificationProvider()
        activeMediaSession?.let { ensureSessionAdded(it) }
    }

    private fun configureNotificationProvider() {
        try {
            val provider = DefaultMediaNotificationProvider.Builder(applicationContext)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
            provider.setSmallIcon(R.drawable.ic_media_notification)
            setMediaNotificationProvider(provider)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    internal fun ensureSessionAdded(session: MediaSession) {
        try {
            // Remove any stale sessions that are no longer active
            val existing = sessions.toList()
            for (old in existing) {
                if (old !== session) {
                    try {
                        removeSession(old)
                    } catch (_: Exception) {}
                }
            }
            if (!sessions.contains(session)) {
                addSession(session)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        val session = activeMediaSession
        if (session != null) {
            ensureSessionAdded(session)
        }
        return session
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        activeMediaSession?.let { ensureSessionAdded(it) }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = activeMediaSession
        if (session == null) {
            stopSelf()
            return
        }
        try {
            val player = session.player
            if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
                stopSelf()
            }
        } catch (_: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        try {
            val existing = sessions.toList()
            for (s in existing) {
                try {
                    removeSession(s)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager != null && manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Controls for active movie and TV playback"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
