package com.zerobook.database

import app.cash.sqldelight.db.SqlDriver

expect fun createDatabaseDriver(): SqlDriver

fun openZeroBookDatabase(): ZeroBookDatabase {
    return ZeroBookDatabase(createDatabaseDriver())
}
