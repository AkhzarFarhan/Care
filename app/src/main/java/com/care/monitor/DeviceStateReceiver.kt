package com.care.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.care.data.EventType
import com.care.data.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeviceStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val eventType = when (intent.action) {
            Intent.ACTION_SCREEN_ON -> EventType.SCREEN_ON
            Intent.ACTION_SCREEN_OFF -> EventType.SCREEN_OFF
            Intent.ACTION_USER_PRESENT -> EventType.UNLOCK
            else -> return
        }
        CoroutineScope(Dispatchers.IO).launch {
            ServiceLocator.eventRepository.record(
                eventType = eventType,
                details = intent.action
            )
        }
    }
}
