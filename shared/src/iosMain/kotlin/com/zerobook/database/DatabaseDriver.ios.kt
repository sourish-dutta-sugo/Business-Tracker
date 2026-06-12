package com.zerobook.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual fun createDatabaseDriver(): SqlDriver = NativeSqliteDriver(
    schema = ZeroBookDatabase.Schema,
    name = "ZeroBook.db",
)
