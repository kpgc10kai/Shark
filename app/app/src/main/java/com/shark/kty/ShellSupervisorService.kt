package com.shark.kty

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Experimental foreground service that starts and keeps the privileged shell
 * server alive. Only used on the experimental branch.
 */
class ShellSupervisorService : Service() {
    companion object {
        private const val TAG = "SharkShell"
        private const val CHANNEL_ID = "shark_shell_quiet"
        private const val NOTIFICATION_ID = 98260
        private const val WAKE_LOCK_TAG = "shark:shell-keeper"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        private const val LOOP_DELAY_MS = 5_000L
        private const val MAX_WAIT_MS = 30_000L
        private const val READY_HOLD_MS = 10_000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, ShellSupervisorService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "start supervisor failed: $it") }
        }
    }

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var waitingSince = 0L
    private var readySince = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(getString(R.string.shell_notification_connecting))
        val powerManager = getSystemService(PowerManager::class.java)
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }
        if (running.compareAndSet(false, true)) {
            worker = Thread({ loop() }, "shark-supervisor").also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running.set(false)
        worker?.interrupt()
        worker = null
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        super.onDestroy()
    }

    private fun loop() {
        var lastState = false
        while (running.get()) {
            try {
                val ready = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
                if (ready != lastState) {
                    lastState = ready
                    val text = getString(
                        if (ready) R.string.shell_notification_running
                        else R.string.shell_notification_connecting
                    )
                    notify(buildNotification(text))
                }
                if (!ready) {
                    readySince = 0L
                    if (waitingSince == 0L) {
                        waitingSince = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - waitingSince > MAX_WAIT_MS &&
                        !WirelessAdb.connected && PrivService.pick() == null
                    ) {
                        Log.i(TAG, "no backend after ${MAX_WAIT_MS}ms; stopping supervisor")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        running.set(false)
                        stopSelf()
                        return
                    }
                    requestBinder()
                    startServerIfPossible()
                } else {
                    waitingSince = 0L
                    if (readySince == 0L) {
                        readySince = System.currentTimeMillis()
                    } else if (System.currentTimeMillis() - readySince > READY_HOLD_MS) {
                        Log.i(TAG, "shell is running; stopping supervisor")
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        running.set(false)
                        stopSelf()
                        return
                    }
                }
            } catch (e: InterruptedException) {
                return
            } catch (t: Throwable) {
                Log.w(TAG, "supervisor loop: $t")
            }
            try {
                Thread.sleep(LOOP_DELAY_MS)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun requestBinder() {
        runCatching {
            val dir = getExternalFilesDir(null) ?: filesDir
            File(dir, "binder-request").writeText(System.currentTimeMillis().toString())
        }
    }

    private fun startServerIfPossible() {
        val starter = File(applicationInfo.nativeLibraryDir, "libstarter.so")
        if (!starter.exists()) {
            Log.w(TAG, "supervisor: libstarter.so missing")
            return
        }
        when (PrivService.pick()) {
            PrivBackend.WIRELESS_ADB -> {
                val command = "setsid ${starter.absolutePath} </dev/null >/dev/null 2>&1"
                val result = runCatching { WirelessAdb.runShell(command) }.getOrNull()
                Log.i(TAG, "supervisor start via adb rc=${result?.first}")
            }
            PrivBackend.STELLAR, PrivBackend.SHIZUKU -> {
                runCatching {
                    PrivService.newProcess(arrayOf(starter.absolutePath), null, "/")
                }.onFailure { Log.w(TAG, "supervisor start via binder failed: $it") }
                Log.i(TAG, "supervisor start via ${PrivService.pick()?.label}")
            }
            null -> return
        }
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.shell_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.shell_channel_desc)
                setShowBadge(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.shell_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startForegroundCompat(text: String) {
        val notification = buildNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "supervisor foreground failed", t)
        }
    }

    private fun notify(notification: Notification) {
        runCatching {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }
}
