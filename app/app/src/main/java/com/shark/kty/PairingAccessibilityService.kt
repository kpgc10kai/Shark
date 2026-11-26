package com.shark.kty

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import roro.stellar.manager.adb.AdbPairingService

/**
 * Reads the six-digit pairing code from the Settings pairing dialog while it is
 * still in the foreground, so the user does not have to type the code.
 */
class PairingAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "SharkA11y"
        private const val RETRY_SAME_CODE_MS = 10_000L
    }

    private var lastCode: String? = null
    private var lastCodeAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "accessibility connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName?.toString() != "com.android.settings") return
        val code = extractCodeFromEvent(event) ?: return
        val now = System.currentTimeMillis()
        if (code == lastCode && now - lastCodeAt < RETRY_SAME_CODE_MS) return
        lastCode = code
        lastCodeAt = now
        Log.i(TAG, "pairing code detected; submitting")
        submit(code)
    }

    private fun submit(code: String) {
        val intent = AdbPairingService.pairIntent(this, code)
        runCatching {
            startForegroundService(intent)
        }.onFailure {
            Log.w(TAG, "pairing service start failed: $it")
        }
    }

    private fun extractCodeFromEvent(event: AccessibilityEvent): String? {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        event.source?.let { roots.add(it) }
        rootInActiveWindow?.let { roots.add(it) }
        runCatching {
            windows.forEach { window ->
                window.root?.let { roots.add(it) }
            }
        }
        return roots.firstNotNullOfOrNull { extractCode(it) }
    }

    private fun extractCode(root: AccessibilityNodeInfo): String? {
        val texts = mutableListOf<String>()
        collect(root, texts, 0)
        if (texts.isEmpty()) return null
        val joined = texts.joinToString(" ").lowercase()
        val looksLikePairing = joined.contains("pair") || joined.contains("配对") ||
            joined.contains("配对码") || joined.contains("pairing")
        if (!looksLikePairing) return null

        val normalized = texts.map { it.replace(Regex("[^0-9]"), "") }
        val exact = normalized.firstOrNull { it.matches(Regex("\\d{6}")) }
        val labelled = Regex(
            "(?:pairing\\s*code|wi-?fi\\s*pairing\\s*code|配对码|配对密码)[^0-9]{0,48}(\\d{6})"
        ).find(joined)?.groupValues?.getOrNull(1)
        return labelled ?: exact
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 30) return
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (index in 0 until node.childCount) {
            collect(node.getChild(index), out, depth + 1)
        }
    }

    override fun onInterrupt() {
    }
}
