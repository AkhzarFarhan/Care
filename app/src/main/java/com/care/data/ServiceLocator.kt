package com.care.data

import android.content.Context
import com.care.firebase.FirebaseSyncRepository
import com.care.monitor.MonitorController

object ServiceLocator {
    lateinit var prefs: CarePreferences
        private set
    lateinit var eventRepository: ActivityEventRepository
        private set
    lateinit var firebaseSyncRepository: FirebaseSyncRepository
        private set
    lateinit var monitorController: MonitorController
        private set

    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = CarePreferences(appContext)
        val database = AppDatabase.get(appContext)
        eventRepository = ActivityEventRepository(database.activityEventDao(), prefs)
        firebaseSyncRepository = FirebaseSyncRepository(eventRepository, prefs)
        monitorController = MonitorController(appContext, prefs)
    }
}
