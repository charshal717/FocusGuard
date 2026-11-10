package com.example.focusguard

import android.content.Context

/** Simple settings storage. Everything stays on the phone. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("focusguard", Context.MODE_PRIVATE)

    // Max minutes per day on distracting apps before the "limit reached" alert
    var dailyLimitMin: Int
        get() = sp.getInt("daily_limit_min", 120)
        set(v) = sp.edit().putInt("daily_limit_min", v).apply()

    // Alert if you stay in one distracting app this long without a break
    var streakMin: Int
        get() = sp.getInt("streak_min", 20)
        set(v) = sp.edit().putInt("streak_min", v).apply()

    // Shown inside every alert so it reminds you WHY
    var goal: String
        get() = sp.getString("goal", "Focus on your studies") ?: "Focus on your studies"
        set(v) = sp.edit().putString("goal", v).apply()

    var monitoring: Boolean
        get() = sp.getBoolean("monitoring", false)
        set(v) = sp.edit().putBoolean("monitoring", v).apply()

    var distractingApps: Set<String>
        get() = sp.getStringSet("distracting", DEFAULT_DISTRACTING) ?: DEFAULT_DISTRACTING
        set(v) = sp.edit().putStringSet("distracting", v).apply()

    // Alert bookkeeping so you are not spammed
    var warnDay: String
        get() = sp.getString("warn_day", "") ?: ""
        set(v) = sp.edit().putString("warn_day", v).apply()

    var limitDay: String
        get() = sp.getString("limit_day", "") ?: ""
        set(v) = sp.edit().putString("limit_day", v).apply()

    var lastOverAlertMs: Long
        get() = sp.getLong("last_over_alert", 0L)
        set(v) = sp.edit().putLong("last_over_alert", v).apply()

    var lastStreakAlertMs: Long
        get() = sp.getLong("last_streak_alert", 0L)
        set(v) = sp.edit().putLong("last_streak_alert", v).apply()

    companion object {
        val DEFAULT_DISTRACTING = setOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "com.snapchat.android",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.netflix.mediaclient",
            "com.zhiliaoapp.musically",
            "in.mohalla.sharechat",
            "com.whatsapp"
        )
    }
}
