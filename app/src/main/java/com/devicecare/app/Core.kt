package com.devicecare.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Simple settings storage. Everything stays on the phone. */
object Config {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun pausedUntil(c: Context): Long = sp(c).getLong("paused", 0L)
    fun setPausedUntil(c: Context, t: Long) = sp(c).edit().putLong("paused", t).apply()
    fun isPaused(c: Context): Boolean = System.currentTimeMillis() < pausedUntil(c)

    fun mode(c: Context, app: AppDef): Mode {
        val s = sp(c).getString("mode_${app.key}", null)
        val parsed = if (s == null) null else runCatching { Mode.valueOf(s) }.getOrNull()
        return parsed ?: app.defaultMode
    }

    fun setMode(c: Context, app: AppDef, m: Mode) =
        sp(c).edit().putString("mode_${app.key}", m.name).apply()

    fun flag(c: Context, key: String, def: Boolean): Boolean = sp(c).getBoolean("f_$key", def)
    fun setFlag(c: Context, key: String, v: Boolean) = sp(c).edit().putBoolean("f_$key", v).apply()
}

/** Password for opening this app. Stored as a salted PBKDF2 hash. */
object Pass {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun isSet(c: Context): Boolean = sp(c).contains("hash")

    private fun hash(pw: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pw.toCharArray(), salt, 60000, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun set(c: Context, pw: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        sp(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", Base64.encodeToString(hash(pw, salt), Base64.NO_WRAP))
            .apply()
    }

    fun check(c: Context, pw: String): Boolean {
        val s = sp(c)
        val salt = s.getString("salt", null) ?: return false
        val h = s.getString("hash", null) ?: return false
        val expected = Base64.decode(h, Base64.NO_WRAP)
        val actual = hash(pw, Base64.decode(salt, Base64.NO_WRAP))
        return MessageDigest.isEqual(expected, actual)
    }
}

/** Helpers for the accessibility service switch. */
object Access {
    fun component(c: Context) = ComponentName(c, GuardService::class.java)

    private fun currentList(c: Context): String =
        Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: ""

    fun isEnabled(c: Context): Boolean {
        val me = component(c)
        return currentList(c).split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    fun hasSecure(c: Context): Boolean =
        c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    /** Turns the service on without opening Settings. Needs the one-time adb grant. */
    fun enable(c: Context): Boolean {
        if (!hasSecure(c)) return false
        return try {
            if (!isEnabled(c)) {
                val cur = currentList(c)
                val me = component(c).flattenToString()
                val next = if (cur.isEmpty()) me else "$cur:$me"
                Settings.Secure.putString(
                    c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next
                )
            }
            Settings.Secure.putInt(c.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            true
        } catch (e: Exception) {
            false
        }
    }
}
