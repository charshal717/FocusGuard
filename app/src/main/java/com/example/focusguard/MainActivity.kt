package com.example.focusguard

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Minimal UI built in code (no XML layouts) so the project stays small. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private lateinit var appList: TextView
    private lateinit var toggle: Button
    private lateinit var goalInput: EditText
    private lateinit var limitInput: EditText
    private lateinit var streakInput: EditText

    private val handler = Handler(Looper.getMainLooper())
    private val refresher = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 5_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 48)
        }

        root.addView(TextView(this).apply {
            text = "FocusGuard"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
        })

        status = TextView(this).apply { textSize = 14f; setPadding(0, 16, 0, 8) }
        root.addView(status)

        root.addView(Button(this).apply {
            text = "Grant Usage Access"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        })

        summary = TextView(this).apply {
            textSize = 20f
            gravity = Gravity.START
            setPadding(0, 32, 0, 8)
        }
        root.addView(summary)

        appList = TextView(this).apply { textSize = 15f; setPadding(0, 8, 0, 24) }
        root.addView(appList)

        root.addView(label("Your goal (shown in every alert)"))
        goalInput = EditText(this).apply { setText(prefs.goal) }
        root.addView(goalInput)

        root.addView(label("Daily limit on distracting apps (minutes)"))
        limitInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.dailyLimitMin.toString())
        }
        root.addView(limitInput)

        root.addView(label("Break reminder after continuous use (minutes)"))
        streakInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(prefs.streakMin.toString())
        }
        root.addView(streakInput)

        root.addView(Button(this).apply {
            text = "Save settings"
            setOnClickListener { saveSettings() }
        })

        toggle = Button(this).apply {
            setOnClickListener { toggleMonitoring() }
        }
        root.addView(toggle)

        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresher)
    }

    override fun onPause() {
        handler.removeCallbacks(refresher)
        super.onPause()
    }

    private fun label(t: String) = TextView(this).apply {
        text = t
        textSize = 13f
        setPadding(0, 24, 0, 0)
    }

    private fun saveSettings() {
        prefs.goal = goalInput.text.toString().ifBlank { "Focus on your studies" }
        prefs.dailyLimitMin = limitInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 120
        prefs.streakMin = streakInput.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 20
        refresh()
    }

    private fun toggleMonitoring() {
        if (!UsageTracker.hasPermission(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return
        }
        saveSettings()
        if (prefs.monitoring) {
            prefs.monitoring = false
            MonitorService.stop(this)
        } else {
            prefs.monitoring = true
            MonitorService.start(this)
        }
        refresh()
    }

    private fun refresh() {
        val granted = UsageTracker.hasPermission(this)
        status.text = when {
            !granted -> "Usage access not granted yet"
            prefs.monitoring -> "Monitoring is ON"
            else -> "Monitoring is OFF"
        }
        toggle.text = if (prefs.monitoring) "Stop monitoring" else "Start monitoring"

        if (!granted) {
            summary.text = ""
            appList.text = "Tap \"Grant Usage Access\", then find FocusGuard in the list and turn it on."
            return
        }

        val snap = UsageTracker.snapshot(this)
        val distracting = prefs.distractingApps
        val totalMin = snap.totalsMs.values.sum() / 60_000
        val distractMin = UsageTracker.distractingTotalMs(snap, distracting) / 60_000

        summary.text = "Today: ${fmt(totalMin)} total\nDistracting: ${fmt(distractMin)} / ${fmt(prefs.dailyLimitMin.toLong())}"

        val top = snap.totalsMs.entries
            .filter { it.value >= 60_000 && it.key != packageName }
            .sortedByDescending { it.value }
            .take(8)
        appList.text = if (top.isEmpty()) "No app usage recorded yet today."
        else top.joinToString("\n") { (pkg, ms) ->
            val mark = if (pkg in distracting) "  ⚠" else ""
            "${appName(pkg)} — ${fmt(ms / 60_000)}$mark"
        }
    }

    private fun appName(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    private fun fmt(min: Long): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"
}
