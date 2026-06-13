package com.zerobook

import android.app.Application
import com.zerobook.database.provideAndroidDatabaseContext

class ZeroBookApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        provideAndroidDatabaseContext(this)
    }
}
