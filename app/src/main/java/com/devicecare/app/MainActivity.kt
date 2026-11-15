package com.devicecare.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

class MainActivity : AppCompatActivity() {

    private var unlockedAt = 0L
    private var refreshing = false

    private lateinit var lockPanel: View
    private lateinit var scroll: View
    private lateinit var etPass: TextInputEditText
    private lateinit var etPass2: TextInputEditText
    private lateinit var tilPass2: TextInputLayout
    private lateinit var tvLockMsg: TextView
    private lateinit var tvLockErr: TextView
    private lateinit var btnUnlock: MaterialButton

    private lateinit var tvStatus: TextView
    private lateinit var btnFix: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnRelease: MaterialButton
    private lateinit var switchEnabled: MaterialSwitch
    private lateinit var switchLogin: MaterialSwitch
    private lateinit var switchWeb: MaterialSwitch
    private lateinit var switchBrowsers: MaterialSwitch
    private lateinit var switchAccounts: MaterialSwitch
    private lateinit var switchDiag: MaterialSwitch
    private lateinit var appList: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lockPanel = findViewById(R.id.lockPanel)
        scroll = findViewById(R.id.scroll)
        etPass = findViewById(R.id.etPass)
        etPass2 = findViewById(R.id.etPass2)
        tilPass2 = findViewById(R.id.tilPass2)
        tvLockMsg = findViewById(R.id.tvLockMsg)
        tvLockErr = findViewById(R.id.tvLockErr)
        btnUnlock = findViewById(R.id.btnUnlock)

        tvStatus = findViewById(R.id.tvStatus)
        btnFix = findViewById(R.id.btnFix)
        btnPause = findViewById(R.id.btnPause)
        btnRelease = findViewById(R.id.btnRelease)
        switchEnabled = findViewById(R.id.switchEnabled)
        switchLogin = findViewById(R.id.switchLogin)
        switchWeb = findViewById(R.id.switchWeb)
        switchBrowsers = findViewById(R.id.switchBrowsers)
        switchAccounts = findViewById(R.id.switchAccounts)
        switchDiag = findViewById(R.id.switchDiag)
        appList = findViewById(R.id.appList)

        wireLock()
        wireSettings()
    }

    override fun onStart() {
        super.onStart()
        val stale = unlockedAt == 0L || SystemClock.elapsedRealtime() - unlockedAt > 120_000L
        if (stale) showLock() else refresh()
    }

    // ------------------------------------------------------------------ lock screen

    private fun wireLock() {
        btnUnlock.setOnClickListener { submitLock() }
        val done = TextView.OnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLock()
                true
            } else {
                false
            }
        }
        etPass.setOnEditorActionListener(done)
        etPass2.setOnEditorActionListener(done)
    }

    private fun showLock() {
        scroll.visibility = View.GONE
        lockPanel.visibility = View.VISIBLE
        etPass.setText("")
        etPass2.setText("")
        tvLockErr.text = ""
        if (Pass.isSet(this)) {
            tvLockMsg.text = "Enter your password to continue."
            tilPass2.visibility = View.GONE
            btnUnlock.text = "Unlock"
        } else {
            tvLockMsg.text = "Create a password. You will need it to change anything here."
            tilPass2.visibility = View.VISIBLE
            btnUnlock.text = "Save password"
        }
    }

    private fun submitLock() {
        val pw = etPass.text?.toString().orEmpty()
        if (!Pass.isSet(this)) {
            val pw2 = etPass2.text?.toString().orEmpty()
            when {
                pw.length < 4 -> tvLockErr.text = "Use at least 4 characters."
                pw != pw2 -> tvLockErr.text = "The two passwords don't match."
                else -> {
                    Pass.set(this, pw)
                    onUnlocked()
                }
            }
        } else if (Pass.check(this, pw)) {
            onUnlocked()
        } else {
            tvLockErr.text = "Wrong password."
            etPass.setText("")
        }
    }

    private fun onUnlocked() {
        unlockedAt = SystemClock.elapsedRealtime()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(etPass.windowToken, 0)
        lockPanel.visibility = View.GONE
        scroll.visibility = View.VISIBLE
        ensureBackground()
        refresh()
    }

    private fun ensureBackground() {
        Policy.apply(this)
        if (Config.enabled(this)) {
            try {
                ContextCompat.startForegroundService(
                    this, Intent(this, WatchdogService::class.java)
                )
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
    }

    // ------------------------------------------------------------------ settings screen

    private fun wireSettings() {
        btnFix.setOnClickListener {
            if (Access.enable(this)) {
                toast("Protection engine turned on")
            } else {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                toast("Find Device Care in the list and switch it on")
            }
            refresh()
        }

        switchEnabled.setOnCheckedChangeListener { _, on ->
            if (!refreshing) {
                Config.setEnabled(this, on)
                if (on) Access.enable(this)
                refresh()
            }
        }
        btnPause.setOnClickListener { pauseDialog() }

        switchLogin.setOnCheckedChangeListener { _, on ->
            if (!refreshing) Config.setFlag(this, "login", on)
        }
        switchWeb.setOnCheckedChangeListener { _, on ->
            if (!refreshing) Config.setFlag(this, "web", on)
        }
        switchBrowsers.setOnCheckedChangeListener { _, on ->
            if (!refreshing) {
                Config.setFlag(this, "browsers", on)
                Policy.apply(this)
            }
        }
        switchAccounts.setOnCheckedChangeListener { _, on ->
            if (!refreshing) {
                Config.setFlag(this, "accounts", on)
                Policy.apply(this)
            }
        }
        switchDiag.setOnCheckedChangeListener { _, on ->
            if (!refreshing) Config.setFlag(this, "diag", on)
        }

        findViewById<View>(R.id.btnClearDiag).setOnClickListener {
            File(filesDir, "diag.txt").delete()
            toast("Recording cleared")
        }
        findViewById<View>(R.id.btnPassword).setOnClickListener { changePasswordDialog() }
        btnRelease.setOnClickListener { releaseDialog() }
    }

    private fun refresh() {
        refreshing = true

        val engine = Access.isEnabled(this)
        val owner = Policy.isOwner(this)
        tvStatus.text = buildString {
            appendLine("Protection engine: " + if (engine) "running" else "NOT running")
            appendLine("Managed mode: " + if (owner) "active" else "not enabled (optional)")
            append(
                "Self-repair: " +
                    if (Access.hasSecure(this@MainActivity)) "ready" else "needs one setup command"
            )
        }
        btnFix.visibility = if (engine) View.GONE else View.VISIBLE

        switchEnabled.isChecked = Config.enabled(this)

        val until = Config.pausedUntil(this)
        val now = System.currentTimeMillis()
        btnPause.text = when {
            until <= now -> "Pause protection…"
            until > now + 3L * 24 * 3600 * 1000 -> "Paused until you resume · tap to resume"
            else -> "Paused · ${((until - now) / 60000) + 1} min left · tap to resume"
        }

        switchLogin.isChecked = Config.flag(this, "login", true)
        switchWeb.isChecked = Config.flag(this, "web", true)
        switchBrowsers.isChecked = Config.flag(this, "browsers", true)
        switchAccounts.isChecked = Config.flag(this, "accounts", false)
        switchDiag.isChecked = Config.flag(this, "diag", false)
        switchBrowsers.isEnabled = owner
        switchAccounts.isEnabled = owner
        btnRelease.visibility = if (owner) View.VISIBLE else View.GONE

        buildAppList()
        refreshing = false
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(app: AppDef): Boolean = app.pkgs.any { p ->
        try {
            packageManager.getPackageInfo(p, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildAppList() {
        appList.removeAllViews()
        for (app in Rules.apps) {
            val v = layoutInflater.inflate(R.layout.item_app, appList, false)
            v.findViewById<TextView>(R.id.appName).text = app.label
            val state = if (isInstalled(app)) "Installed" else "Not installed"
            v.findViewById<TextView>(R.id.appSub).text = "$state · ${app.note}"

            val toggle = v.findViewById<MaterialButtonToggleGroup>(R.id.toggle)
            val bFeat = v.findViewById<MaterialButton>(R.id.btnFeat)
            if (!app.hasFeatures) {
                bFeat.visibility = View.GONE
            } else {
                bFeat.text = when {
                    app.dm != null && app.search != null -> "DMs & search"
                    app.dm != null -> "DMs"
                    else -> "Search"
                }
            }

            var current = Config.mode(this, app)
            if (current == Mode.FEATURES && !app.hasFeatures) current = Mode.OFF
            toggle.check(
                when (current) {
                    Mode.OFF -> R.id.btnOff
                    Mode.FEATURES -> R.id.btnFeat
                    Mode.APP -> R.id.btnApp
                }
            )
            toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val m = when (checkedId) {
                        R.id.btnFeat -> Mode.FEATURES
                        R.id.btnApp -> Mode.APP
                        else -> Mode.OFF
                    }
                    Config.setMode(this, app, m)
                }
            }
            appList.addView(v)
        }
    }

    // ------------------------------------------------------------------ dialogs

    private fun pauseDialog() {
        val paused = Config.isPaused(this)
        val items =
            if (paused) arrayOf("Resume now") else arrayOf("15 minutes", "1 hour", "Until I resume")
        MaterialAlertDialogBuilder(this)
            .setTitle(if (paused) "Protection is paused" else "Pause for how long?")
            .setItems(items) { _, which ->
                val now = System.currentTimeMillis()
                if (paused) {
                    Config.setPausedUntil(this, 0L)
                } else {
                    val until = when (which) {
                        0 -> now + 15L * 60_000L
                        1 -> now + 60L * 60_000L
                        else -> Long.MAX_VALUE / 2
                    }
                    Config.setPausedUntil(this, until)
                }
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun pwField(hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun changePasswordDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 24, 64, 0)
        }
        val cur = pwField("Current password")
        val n1 = pwField("New password")
        val n2 = pwField("Repeat new password")
        box.addView(cur)
        box.addView(n1)
        box.addView(n2)
        MaterialAlertDialogBuilder(this)
            .setTitle("Change password")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val c = cur.text.toString()
                val a = n1.text.toString()
                val b = n2.text.toString()
                when {
                    !Pass.check(this, c) -> toast("Current password is wrong")
                    a.length < 4 -> toast("Use at least 4 characters")
                    a != b -> toast("New passwords don't match")
                    else -> {
                        Pass.set(this, a)
                        toast("Password changed")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun releaseDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Release managed mode?")
            .setMessage(
                "This switches off every phone-level rule (hidden browsers, locked accounts, " +
                    "uninstall protection) and removes this app's device-owner status."
            )
            .setPositiveButton("Release") { _, _ ->
                Policy.release(this)
                toast("Managed mode released")
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
