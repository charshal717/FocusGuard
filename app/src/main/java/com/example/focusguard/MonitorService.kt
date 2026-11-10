package com.example.focusguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private val tick = object : Runnable {
        override fun run() {
            try {
                check()
            } catch (_: Exception) {
                // never crash the service because of one bad read
            }
            handler.postDelayed(this, CHECK_EVERY_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = NotificationCompat.Builder(this, CH_MONITOR)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("FocusGuard is watching")
            .setContentText("Goal: ${prefs.goal}")
            .setOngoing(true)
            .setContentIntent(openAppIntent())
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }

        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- the actual focus logic --------------------------------------------

    private fun check() {
        if (!UsageTracker.hasPermission(this)) return

        val distracting = prefs.distractingApps
        val snap = UsageTracker.snapshot(this)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val now = System.currentTimeMillis()

        val limitMs = prefs.dailyLimitMin * 60_000L
        val usedMs = UsageTracker.distractingTotalMs(snap, distracting)
        val usedMin = usedMs / 60_000

        // 1) 80% warning (once a day)
        if (usedMs >= limitMs * 0.8 && usedMs < limitMs && prefs.warnDay != today) {
            prefs.warnDay = today
            alert(
                10, "Almost at your limit",
                "$usedMin of ${prefs.dailyLimitMin} min used on distracting apps. ${prefs.goal}."
            )
        }

        // 2) Limit reached (once), then a nudge every 30 min while still over
        if (usedMs >= limitMs) {
            if (prefs.limitDay != today) {
                prefs.limitDay = today
                prefs.lastOverAlertMs = now
                alert(
                    11, "Daily limit reached",
                    "You've used $usedMin min on distracting apps today. ${prefs.goal}."
                )
            } else if (now - prefs.lastOverAlertMs >= OVER_NUDGE_MS) {
                prefs.lastOverAlertMs = now
                alert(
                    12, "Still scrolling?",
                    "You're ${usedMin - prefs.dailyLimitMin} min over your limit. ${prefs.goal}."
                )
            }
        }

        // 3) Long continuous session in a distracting app
        val fg = snap.foregroundPkg
        if (fg != null && fg in distracting) {
            val streakMs = now - snap.foregroundSinceMs
            val needMs = prefs.streakMin * 60_000L
            if (streakMs >= needMs && now - prefs.lastStreakAlertMs >= needMs) {
                prefs.lastStreakAlertMs = now
                alert(
                    13, "Time for a break",
                    "${streakMs / 60_000} min straight in ${label(fg)}. ${prefs.goal}."
                )
            }
        }
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    // ---- notifications -----------------------------------------------------

    private fun alert(id: Int, title: String, text: String) {
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MONITOR, "Monitoring status", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "Focus alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        private const val CH_MONITOR = "monitor"
        private const val CH_ALERT = "alerts"
        private const val CHECK_EVERY_MS = 60_000L
        private const val OVER_NUDGE_MS = 30 * 60_000L

        fun start(ctx: Context) {
            val i = Intent(ctx, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, MonitorService::class.java))
        }
    }
}
