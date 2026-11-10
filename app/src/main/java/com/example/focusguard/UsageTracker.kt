package com.example.focusguard

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import java.util.Calendar

data class UsageSnapshot(
    val totalsMs: Map<String, Long>,      // package -> foreground ms today
    val foregroundPkg: String?,           // app on screen right now (if any)
    val foregroundSinceMs: Long           // when that app came to the front
)

object UsageTracker {

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            ctx.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun midnightMs(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Walks today's resume/pause events to get exact foreground time per app. */
    fun snapshot(ctx: Context): UsageSnapshot {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(midnightMs(), now)
        val e = UsageEvents.Event()

        val resumedAt = HashMap<String, Long>()
        val totals = HashMap<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName ?: continue
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = resumedAt.remove(pkg)
                    if (start != null && e.timeStamp > start) {
                        totals[pkg] = (totals[pkg] ?: 0L) + (e.timeStamp - start)
                    }
                }
            }
        }

        // Apps still open right now: count time up to "now"
        var fgPkg: String? = null
        var fgSince = 0L
        for ((pkg, start) in resumedAt) {
            totals[pkg] = (totals[pkg] ?: 0L) + (now - start)
            if (start > fgSince) {
                fgSince = start
                fgPkg = pkg
            }
        }
        return UsageSnapshot(totals, fgPkg, fgSince)
    }

    fun distractingTotalMs(s: UsageSnapshot, distracting: Set<String>): Long =
        s.totalsMs.filterKeys { it in distracting }.values.sum()
}
