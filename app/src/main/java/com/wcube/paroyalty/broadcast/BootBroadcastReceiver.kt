package com.wcube.paroyalty.broadcast

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootBroadcastReceiver: BroadcastReceiver() {
    val ACTION = "android.intent.action.BOOT_COMPLETED"
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION) {
            //run service on boot
            val serviceIntent = Intent(context, BroadcastService::class.java)
            context.startService(serviceIntent)
        }
    }
}