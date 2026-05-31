package com.pckeyboard.ime.util

import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * Returns a [Context] suitable for reading / writing app preferences
 * even before the user has unlocked the device for the first time after
 * boot. In that window the app's normal credential-encrypted storage
 * can't be touched, but the IME service still runs (so it can actually
 * be used to unlock) — fall back to device-protected storage so the
 * SharedPreferences read returns sensible defaults instead of throwing.
 *
 * After unlock, [isUserUnlocked] flips to true and the original context
 * is returned unchanged, so the user's saved settings come back as soon
 * as a fresh instance is constructed.
 */
fun Context.directBootSafeContext(): Context {
    val app = applicationContext
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return app
    val um = app.getSystemService(Context.USER_SERVICE) as? UserManager ?: return app
    return if (!um.isUserUnlocked) app.createDeviceProtectedStorageContext() else app
}
