package com.shark.kty

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Starts the one-tap root flow after boot when the user enabled
 * "Auto root on boot".
 *
 * If the previous automatic attempt never reported completion (for example the
 * device rebooted while it was running), the feature is disabled instead of
 * risking a boot loop.
 */
class AutoRootBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences("pyb110", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        if (prefs.getBoolean(KEY_RUNNING, false)) {
            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .putBoolean(KEY_RUNNING, false)
                .apply()
            Log.w(TAG, "Previous auto root attempt did not finish; disabled")
            return
        }

        prefs.edit().putBoolean(KEY_RUNNING, true).apply()
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(EXTRA_AUTO_ROOT, true)
        }
        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "Could not start auto root: $it") }
    }

    companion object {
        const val EXTRA_AUTO_ROOT = "auto_root"
        const val KEY_ENABLED = "auto_root_on_boot"
        const val KEY_RUNNING = "auto_root_in_progress"
        private const val TAG = "SharkAutoRoot"
    }
}
