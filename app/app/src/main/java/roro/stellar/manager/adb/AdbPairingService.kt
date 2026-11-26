package roro.stellar.manager.adb

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import roro.stellar.manager.compat.BuildUtils.atLeast31
import roro.stellar.manager.compat.BuildUtils.atLeast34
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.shark.kty.R
import roro.stellar.manager.StellarSettings
import kotlin.getValue
import androidx.core.content.edit
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {

        const val notificationChannel = "adb_pairing"
        const val alertNotificationChannel = "adb_pairing_alert"

        private const val tag = "AdbPairingService"

        private const val notificationId = 1
        private const val alertNotificationId = 2
        private const val replyRequestId = 1
        private const val stopRequestId = 2
        private const val retryRequestId = 3
        private const val stopAndRetryRequestId = 4
        private const val startAction = "start"
        private const val stopAction = "stop"
        private const val stopAndRetryAction = "stop_and_retry"
        private const val replyAction = "reply"
        private const val pairAction = "pair_in_app"
        private const val remoteInputResultKey = "paring_code"
        private const val portKey = "paring_code"
        private const val inAppCodeKey = "in_app_pairing_code"

        @Volatile
        private var isRunning = false

        @Volatile
        var pairingActive = false

        fun startIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(startAction)

        /** Pair with a code entered inside the app instead of the notification. */
        fun pairIntent(context: Context, code: String): Intent =
            Intent(context, AdbPairingService::class.java)
                .setAction(pairAction)
                .putExtra(inAppCodeKey, code)

        private fun stopIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(stopAction)

        private fun stopAndRetryIntent(context: Context): Intent =
            Intent(context, AdbPairingService::class.java).setAction(stopAndRetryAction)

        /** Drop every pairing notification and stop discovery. */
        fun cancel(context: Context) {
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.cancel(notificationId)
                manager.cancel(alertNotificationId)
            }
            runCatching { context.stopService(Intent(context, AdbPairingService::class.java)) }
        }

        private fun replyIntent(context: Context, port: Int): Intent =
            Intent(context, AdbPairingService::class.java).setAction(replyAction).putExtra(portKey, port)
    }

    private var adbMdns: AdbMdns? = null
    private val retryHandler = Handler(Looper.getMainLooper())
    private var discoveredPort: Int = -1
    private var retryAttempts = 0

    private val retryRunnable = object : Runnable {
        override fun run() {
            if (!started || discoveredPort > 0) return
            if (retryAttempts >= 8) {
                Log.w(tag, "discovery retry limit reached")
                onSearchMaxRefresh()
                return
            }
            retryAttempts++
            Log.i(tag, "restarting discovery (attempt $retryAttempts)")
            stopSearch()
            adbMdns?.destroy()
            adbMdns = null
            startSearch()
            retryHandler.postDelayed(this, 6000)
        }
    }

    private val observer = Observer<Int> { port ->
        Log.i(tag, "pairing service port: $port")
        if (port <= 0 || !pairingActive) {
            return@Observer
        }

        discoveredPort = port
        retryAttempts = 0
        retryHandler.removeCallbacks(retryRunnable)

        val notification = createInputNotification(port)
        try {
            if (atLeast34) {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
            Log.i(tag, "notification updated: enter pairing code")
        } catch (e: Exception) {
            Log.e(tag, "failed to update notification", e)
            getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        }

        // Separate high-importance notification so the system shows a heads-up
        // with the code input, even when the foreground notification is
        // minimized.
        showPairingAlert(port)
    }

    private fun showPairingAlert(port: Int) {
        if (!pairingActive) return
        val alertNotification = Notification.Builder(this, alertNotificationChannel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.pairing_service_found))
            .setContentText(getString(R.string.enter_pairing_code))
            .setContentIntent(contentIntent)
            .addAction(replyNotificationAction(port))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
        getSystemService(NotificationManager::class.java).notify(alertNotificationId, alertNotification)
    }

    private var started = false

    override fun onCreate() {
        super.onCreate()

        val notificationManager = getSystemService(NotificationManager::class.java)

        notificationManager.createNotificationChannel(
            NotificationChannel(
                notificationChannel,
                getString(R.string.wireless_debugging_pairing_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                setAllowBubbles(false)
            })

        notificationManager.createNotificationChannel(
            NotificationChannel(
                alertNotificationChannel,
                getString(R.string.pairing_alert_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setShowBadge(true)
                enableVibration(true)
            })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = when (intent?.action) {
            startAction -> {
                onStart()
            }
            replyAction -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(remoteInputResultKey) ?: ""
                val port = intent.getIntExtra(portKey, -1)
                if (port != -1) {
                    onInput(code.toString(), port)
                } else {
                    onStart()
                }
            }
            stopAction -> {
                onStopSearch()
            }
            stopAndRetryAction -> {
                onStopAndRetry()
            }
            pairAction -> {
                val code = intent.getStringExtra(inAppCodeKey) ?: ""
                onPairCommand(code)
            }
            else -> {
                return START_NOT_STICKY
            }
        }
        try {
            if (atLeast34) {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Throwable) {
            Log.e(tag, "failed to start foreground service", e)

            if (atLeast31
                && e is ForegroundServiceStartNotAllowedException) {
                getSystemService(NotificationManager::class.java).notify(notificationId, notification)
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startSearch() {
        if (started) return
        started = true
        adbMdns?.destroy()
        adbMdns = null
        adbMdns = AdbMdns(
            this,
            AdbMdns.TLS_PAIRING,
            observer,
            onMaxRefresh = { onSearchMaxRefresh() }
        ).apply { start() }
    }

    private fun stopSearch() {
        started = false
        runCatching {
            getSystemService(NotificationManager::class.java).cancel(alertNotificationId)
        }
        try {
            adbMdns?.stop()
        } catch (e: Exception) {
            Log.e(tag, "failed to stop discovery", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        pairingActive = false
        retryHandler.removeCallbacksAndMessages(null)
        stopSearch()
        adbMdns?.destroy()
        adbMdns = null
        connectMdns?.destroy()
        connectMdns = null
    }

    private fun onStart(): Notification {
        isRunning = true
        pairingActive = true
        stopSearch()
        adbMdns?.destroy()
        adbMdns = null
        discoveredPort = -1
        retryAttempts = 0
        retryHandler.removeCallbacks(retryRunnable)
        // Let the previous discovery finish stopping before registering again.
        retryHandler.postDelayed({ startSearch() }, 700)
        return searchingNotification
    }

    private fun onStopSearch(): Notification {
        stopSearch()
        return createManualInputNotification(discoveredPort)
    }

    private fun onStopAndRetry(): Notification {
        stopSearch()
        adbMdns?.destroy()
        adbMdns = null
        return onStart()
    }

    private fun onSearchMaxRefresh() {
        Log.i(tag, "discovery attempt limit reached")
        stopSearch()
        val notification = createMaxRefreshNotification()
        try {
            if (atLeast34) {
                startForeground(notificationId, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(notificationId, notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "failed to update foreground notification", e)
            getSystemService(NotificationManager::class.java).notify(notificationId, notification)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun onInput(code: String, port: Int): Notification {
        if (port <= 0) {
            GlobalScope.launch(Dispatchers.IO) {
                val ok = pairAcrossCandidates(code)
                handleResult(ok)
            }
            return workingNotification
        }

        GlobalScope.launch(Dispatchers.IO) {
            val host = "127.0.0.1"

            val key = try {
                AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "Stellar")
            } catch (e: Throwable) {
                e.printStackTrace()
                return@launch
            }

            AdbPairingClient(host, port, code, key).runCatching {
                start()
            }.onFailure {
                Log.e(tag, "pairing failed", it)
                handleResult(false)
            }.onSuccess {
                handleResult(it)
            }
        }

        return workingNotification
    }

    private var connectMdns: AdbMdns? = null

    @OptIn(DelicateCoroutinesApi::class)
    private fun onPairCommand(code: String): Notification {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return searchingNotification
        }
        Log.i(tag, "pairing code submitted in app (${trimmed.length} digits)")
        val port = discoveredPort
        GlobalScope.launch(Dispatchers.IO) {
            var ok = false
            if (port > 0) {
                ok = pairOnPort(trimmed, port)
                Log.i(tag, "in-app pair on discovered port $port: $ok")
            }
            if (!ok) {
                Log.i(tag, "falling back to a local port scan")
                ok = pairAcrossCandidates(trimmed)
            }
            handleResult(ok)
        }
        return workingNotification
    }

    private fun pairOnPort(code: String, port: Int): Boolean {
        val key = runCatching {
            AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "Stellar")
        }.getOrNull() ?: return false
        return runCatching {
            AdbPairingClient(
                "127.0.0.1", port, code, key,
                connectTimeoutMs = 3_000,
                readTimeoutMs = 8_000,
            ).start()
        }.getOrDefault(false)
    }

    private fun pairAcrossCandidates(code: String): Boolean {
        val candidates = candidatePorts()
        Log.i(tag, "pairing candidates (${candidates.size}): $candidates")
        val deadline = System.currentTimeMillis() + 20_000L
        for (port in candidates) {
            if (System.currentTimeMillis() >= deadline) {
                Log.w(tag, "pairing candidate deadline reached")
                break
            }
            val ok = pairOnPort(code, port)
            Log.i(tag, "pair attempt on $port: $ok")
            if (ok) return true
        }
        return false
    }

    /** Listening ports from /proc, preferring the ephemeral range used by adbd. */
    private fun candidatePorts(): List<Int> {
        val ports = LinkedHashSet<Int>()
        listOf("/proc/net/tcp", "/proc/net/tcp6").forEach { path ->
            runCatching {
                File(path).useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size > 3 && parts[3] == "0A") {
                            val port = parts[1].substringAfterLast(':')
                                .toIntOrNull(16) ?: return@forEach
                            if (port in 1024..65535) ports.add(port)
                        }
                    }
                }
            }
        }
        val preferred = ports.filter { it in 30000..50100 }
        if (preferred.isNotEmpty()) return preferred.sorted()
        if (ports.isNotEmpty()) return ports.sorted()
        return scanOpenPorts()
    }

    private fun scanOpenPorts(): List<Int> {
        val found = Collections.synchronizedList(mutableListOf<Int>())
        val pool = Executors.newFixedThreadPool(128)
        for (port in 30000..50000) {
            pool.execute {
                runCatching {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress("127.0.0.1", port), 20)
                        found.add(port)
                    }
                }
            }
        }
        pool.shutdown()
        runCatching { pool.awaitTermination(30, TimeUnit.SECONDS) }
        return found.sorted()
    }

    private fun handleResult(success: Boolean) {
        retryHandler.post {
            if (success) {
                Log.i(tag, "paired; searching for the connect service")
                pairingActive = false
                runCatching {
                    getSystemService(NotificationManager::class.java).cancel(alertNotificationId)
                }
                stopSearch()

                val successNotification = Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.pairing_success))
                    .setContentText(getString(R.string.searching_connect_service))
                    .setOngoing(true)
                    .build()

                try {
                    if (atLeast34) {
                        startForeground(notificationId, successNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(notificationId, successNotification)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "failed to update foreground notification", e)
                }

                searchConnectService()
            } else {
                val title = getString(R.string.pairing_failed_retrying)
                val text = getString(R.string.please_wait_auto_return)

                Log.i(tag, "pairing failed; retrying")

                val failureNotification = Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setOngoing(true)
                    .build()

                try {
                    if (atLeast34) {
                        startForeground(notificationId, failureNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    } else {
                        startForeground(notificationId, failureNotification)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "failed to update foreground notification", e)
                }

                retryHandler.postDelayed({
                    val retryNotification = createManualInputNotification(discoveredPort)
                    try {
                        if (atLeast34) {
                            startForeground(notificationId, retryNotification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                        } else {
                            startForeground(notificationId, retryNotification)
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "failed to update foreground notification", e)
                    }
                }, 2000)
            }
        }
    }

    private fun searchConnectService() {
        val connectObserver = Observer<Int> { port ->
            Log.i(tag, "connect service port: $port")
            if (port <= 0) return@Observer

            connectMdns?.destroy()
            connectMdns = null

            onConnectServiceFound(port)
        }

        connectMdns = AdbMdns(
            this,
            AdbMdns.TLS_CONNECT,
            connectObserver,
            onMaxRefresh = {
                Log.w(tag, "connect service discovery limit reached")
                onConnectServiceMaxRefresh()
            }
        ).apply { start() }
    }

    private fun onConnectServiceFound(port: Int) {
        retryHandler.post {
            Log.i(tag, "found connect service port: $port")

            val preferences = StellarSettings.getPreferences()
            val tcpipPortEnabled = preferences.getBoolean(StellarSettings.TCPIP_PORT_ENABLED, true)
            val currentPort = preferences.getString(StellarSettings.TCPIP_PORT, "")

            if (tcpipPortEnabled && currentPort.isNullOrEmpty()) {
                preferences.edit {
                    putString(StellarSettings.TCPIP_PORT, port.toString())
                }
                Log.i(tag, "auto-set TCP port: $port")
            }

            grantSecureSettingsPermission(port)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun grantSecureSettingsPermission(port: Int) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val key = AdbKey(PreferenceAdbKeyStore(StellarSettings.getPreferences()), "stellar")

                val maxWait = 5000L
                val interval = 200L
                var elapsed = 0L
                while (elapsed < maxWait) {
                    try {
                        java.net.Socket("127.0.0.1", port).close()
                        break
                    } catch (_: Exception) {
                        kotlinx.coroutines.delay(interval)
                        elapsed += interval
                    }
                }

                AdbClient("127.0.0.1", port, key).use { client ->
                    client.connect()
                    val command = "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
                    client.shellCommand(command) { output ->
                        Log.d(tag, "grant command output: ${String(output)}")
                    }
                    // Bring the app back to the foreground through the shell user;
                    // a background service cannot start activities on Android 17.
                    val openApp = "am start -n $packageName/.MainActivity --ei adb_pairing_port $port"
                    runCatching {
                        client.shellCommand(openApp) { output ->
                            Log.d(tag, "open app output: ${String(output)}")
                        }
                    }.onFailure {
                        Log.w(tag, "am start failed", it)
                    }
                }
                Log.i(tag, "WRITE_SECURE_SETTINGS granted")
            } catch (e: Exception) {
                Log.e(tag, "failed to grant WRITE_SECURE_SETTINGS", e)
            }

            retryHandler.post {
                navigateToStarter(port)
            }
        }
    }

    private fun navigateToStarter(port: Int) {
        val intent = Intent(this, com.shark.kty.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("adb_pairing_port", port)
        }
        startActivity(intent)

        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(notificationId)
            manager.cancel(alertNotificationId)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        isRunning = false
        pairingActive = false
        stopSelf()
    }

    private fun onConnectServiceMaxRefresh() {
        retryHandler.post {
            Log.w(tag, "connect service discovery limit reached; trying the system port")

            val systemPort = roro.stellar.manager.util.EnvironmentUtils.getAdbTcpPort()
            if (systemPort in 1..65535) {
                grantSecureSettingsPermission(systemPort)
            } else {
                val notification = Notification.Builder(this, notificationChannel)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(getString(R.string.connect_service_not_found))
                    .setContentText(getString(R.string.please_open_app_manually))
                    .setAutoCancel(true)
                    .build()

                stopForeground(STOP_FOREGROUND_REMOVE)
                getSystemService(NotificationManager::class.java).notify(notificationId, notification)
                isRunning = false
                stopSelf()
            }
        }
    }

    private val stopNotificationAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this,
            stopRequestId,
            stopIntent(this),
            if (atLeast31)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.stop_search),
            pendingIntent
        )
            .build()
    }

    private val retryNotificationAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this,
            retryRequestId,
            startIntent(this),
            if (atLeast31)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.adb_retry),
            pendingIntent
        )
            .build()
    }

    private val stopAndRetryNotificationAction by lazy {
        val pendingIntent = PendingIntent.getService(
            this,
            stopAndRetryRequestId,
            stopAndRetryIntent(this),
            if (atLeast31)
                PendingIntent.FLAG_IMMUTABLE
            else
                0
        )

        Notification.Action.Builder(
            null,
            getString(R.string.cannot_find_pairing),
            pendingIntent
        )
            .build()
    }

    private fun replyNotificationAction(port: Int): Notification.Action {
        val remoteInput = RemoteInput.Builder(remoteInputResultKey).run {
            setLabel(getString(R.string.pairing_code))
            build()
        }

        // Use a per-port request code so the pending intent always carries the
        // port the pairing service was discovered on.
        val pendingIntent = PendingIntent.getForegroundService(
            this,
            10000 + port,
            replyIntent(this, port),
            if (atLeast31)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Action.Builder(
            null,
            getString(R.string.enter_pairing_code_action),
            pendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()
    }

    private val contentIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.shark.kty.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            if (atLeast31)
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private val searchingNotification by lazy {
        Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.searching_pairing_service))
            .setContentIntent(contentIntent)
            .addAction(replyNotificationAction(-1))
            .addAction(stopNotificationAction)
            .addAction(stopAndRetryNotificationAction)
            .build()
    }

    private fun createInputNotification(port: Int): Notification =
        Notification.Builder(this, notificationChannel)
            .setContentTitle(getString(R.string.pairing_service_found))
            .setContentIntent(contentIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .addAction(replyNotificationAction(port))
            .build()

    private fun createMaxRefreshNotification(): Notification =
        Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.pairing_service_not_found))
            .setContentIntent(contentIntent)
            .setContentText(getString(R.string.ensure_wireless_debugging_open))
            .addAction(retryNotificationAction)
            .build()

    private val workingNotification by lazy {
        Notification.Builder(this, notificationChannel)
            .setContentTitle(getString(R.string.pairing_in_progress))
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    private fun createManualInputNotification(port: Int): Notification =
        Notification.Builder(this, notificationChannel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.search_stopped))
            .setContentIntent(contentIntent)
            .setContentText(if (port > 0) getString(R.string.enter_pairing_code) else getString(R.string.pairing_service_not_found_retry))
            .addAction(if (port > 0) replyNotificationAction(port) else retryNotificationAction)
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}

