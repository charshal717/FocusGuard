package com.example.focusguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts monitoring after the phone reboots (if you had it switched on). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs(context).monitoring) {
            MonitorService.start(context)
        }
    }
}
