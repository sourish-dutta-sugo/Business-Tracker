package com.zerobook.database

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver

private lateinit var applicationContext: Context

fun provideAndroidDatabaseContext(context: Context) {
    applicationContext = context.applicationContext
}

fun applicationContextOrNull(): Context? = applicationContext.takeIf { ::applicationContext.isInitialized }

actual fun createDatabaseDriver(): SqlDriver {
    check(::applicationContext.isInitialized) {
        "Android database context was not provided before opening ZeroBookDatabase."
    }
    return AndroidSqliteDriver(ZeroBookDatabase.Schema, applicationContext, "ZeroBook.db")
}
