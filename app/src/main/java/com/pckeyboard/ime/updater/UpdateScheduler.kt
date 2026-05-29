package com.pckeyboard.ime.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pckeyboard.ime.settings.KeyboardPrefs
import java.util.concurrent.TimeUnit

/**
 * Manages the WorkManager schedule for the periodic update check. Idempotent
 * — calling [schedule] every time the IME boots or the settings activity
 * opens is fine; the same uniqueWorkName is reused.
 */
object UpdateScheduler {

    private const val UNIQUE_NAME = "pck_update_check"

    /** Enqueue (or replace) the periodic UpdateCheckWorker per the user's
     *  current preferences. Pass [replace] = true after the user changes
     *  the interval or toggles the master switch. */
    fun schedule(context: Context, replace: Boolean = false) {
        val prefs = KeyboardPrefs(context)
        if (!prefs.autoUpdateEnabled) {
            cancel(context)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            prefs.autoUpdateIntervalHours.toLong(), TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(1L, TimeUnit.HOURS)
            .build()
        val policy = if (replace) ExistingPeriodicWorkPolicy.UPDATE
                     else ExistingPeriodicWorkPolicy.KEEP
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(UNIQUE_NAME, policy, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_NAME)
    }
}
