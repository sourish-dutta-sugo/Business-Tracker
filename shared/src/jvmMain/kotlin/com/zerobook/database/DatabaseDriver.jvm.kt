package com.zerobook.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File

actual fun createDatabaseDriver(): SqlDriver {
    val dataDir = File(System.getProperty("user.home"), ".zerobook")
    if (!dataDir.exists()) {
        dataDir.mkdirs()
    }

    val databaseFile = File(dataDir, "ZeroBook.db")
    val isNewDatabase = !databaseFile.exists()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")

    if (isNewDatabase) {
        ZeroBookDatabase.Schema.create(driver)
    }

    return driver
}
