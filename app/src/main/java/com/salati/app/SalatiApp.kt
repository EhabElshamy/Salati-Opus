package com.salati.app

import android.app.Application
import com.salati.app.data.SalatiDatabase
import com.salati.app.data.SalatiRepository
import com.salati.app.notify.Notifications

class SalatiApp : Application() {
    lateinit var repository: SalatiRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = SalatiRepository(SalatiDatabase.create(this))
        Notifications.createChannels(this)
    }
}
