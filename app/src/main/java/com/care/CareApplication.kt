package com.care

import android.app.Application
import com.care.data.ServiceLocator

class CareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
