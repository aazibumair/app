package com.devicecare.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import androidx.core.content.ContextCompat

/** Registered as device owner with: adb shell dpm set-device-owner com.devicecare.app/.AdminReceiver */
class AdminReceiver : DeviceAdminReceiver()

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Policy.apply(context)
        try {
            ContextCompat.startForegroundService(
                context, Intent(context, WatchdogService::class.java)
            )
        } catch (_: Exception) {
        }
    }
}

/** Phone-level rules. Only works when this app is the device owner (managed phone). */
object Policy {

    private fun dpm(c: Context) =
        c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun admin(c: Context) = ComponentName(c, AdminReceiver::class.java)

    fun isOwner(c: Context): Boolean = dpm(c).isDeviceOwnerApp(c.packageName)

    private val alwaysRestrictions = listOf(
        UserManager.DISALLOW_SAFE_BOOT,
        UserManager.DISALLOW_FACTORY_RESET,
        UserManager.DISALLOW_ADD_USER,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
    )

    private fun restrict(c: Context, key: String, on: Boolean) {
        try {
            if (on) dpm(c).addUserRestriction(admin(c), key)
            else dpm(c).clearUserRestriction(admin(c), key)
        } catch (_: Exception) {
        }
    }

    private fun setBrowsersHidden(c: Context, hidden: Boolean) {
        for (pkg in Rules.browsers) {
            try {
                dpm(c).setApplicationHidden(admin(c), pkg, hidden)
            } catch (_: Exception) {
            }
        }
    }

    /** Applies every rule that is switched on. Safe to call repeatedly. */
    fun apply(c: Context) {
        if (!isOwner(c)) return
        val d = dpm(c)
        val a = admin(c)
        try {
            d.setUninstallBlocked(a, c.packageName, true)
        } catch (_: Exception) {
        }
        alwaysRestrictions.forEach { restrict(c, it, true) }
        restrict(c, UserManager.DISALLOW_MODIFY_ACCOUNTS, Config.flag(c, "accounts", false))
        try {
            d.setPermittedAccessibilityServices(a, listOf(c.packageName))
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                d.setUserControlDisabledPackages(a, listOf(c.packageName))
            } catch (_: Exception) {
            }
        }
        setBrowsersHidden(c, Config.flag(c, "browsers", true))
    }

    /** Undoes everything and gives up device-owner status. */
    @Suppress("DEPRECATION")
    fun release(c: Context) {
        if (!isOwner(c)) return
        val d = dpm(c)
        val a = admin(c)
        alwaysRestrictions.forEach { restrict(c, it, false) }
        restrict(c, UserManager.DISALLOW_MODIFY_ACCOUNTS, false)
        setBrowsersHidden(c, false)
        try {
            d.setPermittedAccessibilityServices(a, null)
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                d.setUserControlDisabledPackages(a, emptyList())
            } catch (_: Exception) {
            }
        }
        try {
            d.setUninstallBlocked(a, c.packageName, false)
        } catch (_: Exception) {
        }
        try {
            d.clearDeviceOwnerApp(c.packageName)
        } catch (_: Exception) {
        }
    }
}
