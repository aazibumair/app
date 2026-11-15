package com.devicecare.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Watches the apps you chose. When it sees a blocked screen (messages, search, sign-in)
 * it covers the screen for a moment and presses Back (or Home).
 * Nothing is stored or sent anywhere. The only exception is the optional Debug recording,
 * which writes screen labels to a local file you can read over USB.
 */
class GuardService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pending = false
    private var cooldownUntil = 0L
    private val recent = mutableListOf<Long>()
    private var overlay: View? = null
    private var lastSig = 0
    private var lastDumpAt = 0L

    private val byPkg: Map<String, AppDef> =
        Rules.apps.flatMap { app -> app.pkgs.map { it to app } }.toMap()

    private val diagFile: File by lazy { File(filesDir, "diag.txt") }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            ContextCompat.startForegroundService(this, Intent(this, WatchdogService::class.java))
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        if (!Config.enabled(this) || Config.isPaused(this)) return
        if (byPkg[pkg] == null && pkg !in Rules.browsers) return
        if (SystemClock.uptimeMillis() < cooldownUntil) return
        if (pending) return
        pending = true
        handler.postDelayed({
            pending = false
            try {
                evaluate()
            } catch (_: Exception) {
            }
        }, 120)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- decisions

    private fun evaluate() {
        if (SystemClock.uptimeMillis() < cooldownUntil) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        val app = byPkg[pkg]

        if (app != null) {
            val mode = Config.mode(this, app)
            val diag = Config.flag(this, "diag", false)
            if (mode == Mode.APP) {
                block("This app is turned off on this device.", forceHome = true)
                return
            }
            if (mode == Mode.OFF && !diag) return
            val hit = scan(root, app, mode, diag)
            if (mode != Mode.OFF && hit != null) block(hit)
            return
        }

        if (pkg in Rules.browsers && Config.flag(this, "web", true)) {
            checkBrowser(root, pkg)
        }
    }

    private fun checkBrowser(root: AccessibilityNodeInfo, pkg: String) {
        var url: String? = null
        for (suffix in Rules.urlBarIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId("$pkg:id/$suffix")
            val t = nodes?.firstOrNull()?.text?.toString()
            if (!t.isNullOrBlank()) {
                url = t.lowercase()
                break
            }
        }
        if (url == null) return
        for (app in Rules.apps) {
            if (Config.mode(this, app) == Mode.OFF) continue
            if (app.webDomains.any { url.contains(it) }) {
                block("This website is turned off on this device.", forceHome = true)
                return
            }
        }
    }

    // ---------------------------------------------------------------- screen scan

    private fun startsWithAny(s: String?, list: List<String>): Boolean {
        if (s.isNullOrBlank() || list.isEmpty()) return false
        val t = s.trim()
        return list.any { t.startsWith(it, ignoreCase = true) }
    }

    private fun equalsAny(s: String?, list: List<String>): Boolean {
        if (s.isNullOrBlank() || list.isEmpty()) return false
        val t = s.trim()
        return list.any { t.equals(it, ignoreCase = true) }
    }

    private fun matches(
        sig: Sig?, id: String?, text: String?, desc: String?, hint: String?,
        selected: Boolean, visible: Boolean, isEdit: Boolean
    ): Boolean {
        if (sig == null) return false
        if (visible && id != null && sig.ids.any { id.contains(it) }) return true
        if (selected && (startsWithAny(text, sig.selected) || startsWithAny(desc, sig.selected))) {
            return true
        }
        if (isEdit && visible && (startsWithAny(hint, sig.hints) || startsWithAny(text, sig.hints))) {
            return true
        }
        if (visible && (equalsAny(text, sig.texts) || equalsAny(desc, sig.texts))) return true
        return false
    }

    private fun clip(s: String?): String = s?.replace('\n', ' ')?.take(60) ?: ""

    private fun scan(root: AccessibilityNodeInfo, app: AppDef, mode: Mode, diag: Boolean): String? {
        val wantFeatures = mode == Mode.FEATURES
        val wantLogin = mode != Mode.OFF && Config.flag(this, "login", true)
        val dump = if (diag) StringBuilder() else null
        var hit: String? = null

        val stack = ArrayList<AccessibilityNodeInfo>()
        stack.add(root)
        var budget = 1500
        while (stack.isNotEmpty() && budget > 0) {
            budget--
            val n = stack.removeAt(stack.size - 1)
            val id = n.viewIdResourceName?.lowercase()
            val text = n.text?.toString()
            val desc = n.contentDescription?.toString()
            val hint = n.hintText?.toString()
            val visible = n.isVisibleToUser
            val selected = n.isSelected
            val isEdit = n.className?.toString()?.contains("EditText") == true

            if (dump != null &&
                (id != null || !text.isNullOrBlank() || !desc.isNullOrBlank() || !hint.isNullOrBlank())
            ) {
                dump.append(clip(id)).append('|')
                    .append(clip(text)).append('|')
                    .append(clip(desc)).append('|')
                    .append(clip(hint)).append('|')
                    .append(if (selected) "S" else "-")
                    .append(if (visible) "V" else "-")
                    .append('\n')
            }

            if (hit == null) {
                if (wantFeatures) {
                    if (matches(app.dm, id, text, desc, hint, selected, visible, isEdit)) {
                        hit = "Messages are turned off on this device."
                    } else if (matches(app.search, id, text, desc, hint, selected, visible, isEdit)) {
                        hit = "Search is turned off on this device."
                    }
                }
                if (hit == null && wantLogin &&
                    matches(app.login, id, text, desc, hint, selected, visible, isEdit)
                ) {
                    hit = "Signing in and adding accounts is turned off on this device."
                }
            }

            if (hit != null && dump == null) break
            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.add(it) }
            }
        }

        if (dump != null) writeDump(root.packageName?.toString() ?: app.key, dump.toString(), hit)
        return hit
    }

    private fun writeDump(pkg: String, body: String, hit: String?) {
        val now = SystemClock.uptimeMillis()
        val sig = body.hashCode()
        if (sig == lastSig || now - lastDumpAt < 1000) return
        lastSig = sig
        lastDumpAt = now
        try {
            if (diagFile.exists() && diagFile.length() > 600_000) diagFile.delete()
            val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val verdict = if (hit != null) "BLOCKED: $hit" else "ok"
            diagFile.appendText("=== $time $pkg $verdict\n$body\n")
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- action

    private fun block(message: String, forceHome: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        recent.removeAll { now - it > 2500 }
        recent.add(now)
        showOverlay(message)
        if (forceHome || recent.size >= 2) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            recent.clear()
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        cooldownUntil = now + 500
    }

    private fun showOverlay(message: String) {
        try {
            removeOverlay()
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val tv = TextView(this).apply {
                text = message
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER
                setBackgroundColor(0xF2102A2A.toInt())
                setPadding(64, 64, 64, 64)
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(tv, lp)
            overlay = tv
            handler.postDelayed({ removeOverlay() }, 900)
        } catch (_: Exception) {
        }
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        overlay = null
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (_: Exception) {
        }
    }
}
