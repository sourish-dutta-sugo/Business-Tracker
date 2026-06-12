package com.zerobook.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.createDefaultWebWorkerDriver

actual fun createDatabaseDriver(): SqlDriver = createDefaultWebWorkerDriver()
