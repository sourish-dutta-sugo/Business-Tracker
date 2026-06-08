package com.example.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

fun ensureVoucherExtensionColumns(db: SupportSQLiteDatabase) {
    val voucherColumns = db.query("PRAGMA table_info(vouchers)").use { cursor ->
        buildSet {
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                add(cursor.getString(nameIndex))
            }
        }
    }

    fun ensureColumn(name: String, sql: String) {
        if (name !in voucherColumns) {
            db.execSQL(sql)
        }
    }

    ensureColumn("payment_mode", "ALTER TABLE vouchers ADD COLUMN payment_mode TEXT DEFAULT ''")
    ensureColumn("partial_amount_paid", "ALTER TABLE vouchers ADD COLUMN partial_amount_paid REAL DEFAULT 0")
    ensureColumn("partial_payment_submode", "ALTER TABLE vouchers ADD COLUMN partial_payment_submode TEXT DEFAULT ''")
    ensureColumn("credit_due_date", "ALTER TABLE vouchers ADD COLUMN credit_due_date TEXT DEFAULT ''")
    ensureColumn("remaining_credit_amount", "ALTER TABLE vouchers ADD COLUMN remaining_credit_amount REAL DEFAULT 0")
    ensureColumn("is_advance", "ALTER TABLE vouchers ADD COLUMN is_advance INTEGER DEFAULT 0")
    ensureColumn("advance_for", "ALTER TABLE vouchers ADD COLUMN advance_for TEXT DEFAULT ''")
    ensureColumn("transport_name", "ALTER TABLE vouchers ADD COLUMN transport_name TEXT DEFAULT ''")
    ensureColumn("transport_vehicle", "ALTER TABLE vouchers ADD COLUMN transport_vehicle TEXT DEFAULT ''")
    ensureColumn("transport_lr_no", "ALTER TABLE vouchers ADD COLUMN transport_lr_no TEXT DEFAULT ''")
    ensureColumn("transport_gstin", "ALTER TABLE vouchers ADD COLUMN transport_gstin TEXT DEFAULT ''")
    ensureColumn("transport_destination", "ALTER TABLE vouchers ADD COLUMN transport_destination TEXT DEFAULT ''")
}

fun ensureBusinessProfileExtensionColumns(db: SupportSQLiteDatabase) {
    val profileColumns = db.query("PRAGMA table_info(business_profile)").use { cursor ->
        buildSet {
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                add(cursor.getString(nameIndex))
            }
        }
    }

    if ("bank_branch" !in profileColumns) {
        db.execSQL("ALTER TABLE business_profile ADD COLUMN bank_branch TEXT DEFAULT ''")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE parties ADD COLUMN pin TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE products ADD COLUMN batch_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE products ADD COLUMN batch_number TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE products ADD COLUMN expiry_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE products ADD COLUMN expiry_date TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE products ADD COLUMN serial_enabled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE vouchers ADD COLUMN attachment_path TEXT DEFAULT ''")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val businessProfileColumns = db.query("PRAGMA table_info(business_profile)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }
        if ("fyLabel" !in businessProfileColumns && "fy_label" !in businessProfileColumns) {
            db.execSQL("ALTER TABLE business_profile ADD COLUMN fyLabel TEXT NOT NULL DEFAULT '2025-26'")
        }

        val voucherColumns = db.query("PRAGMA table_info(vouchers)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        }
        if ("additionalChargesJson" !in voucherColumns && "additional_charges_json" !in voucherColumns) {
            db.execSQL("ALTER TABLE vouchers ADD COLUMN additionalChargesJson TEXT NOT NULL DEFAULT '[]'")
        }
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureVoucherExtensionColumns(db)
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        ensureVoucherExtensionColumns(db)
        ensureBusinessProfileExtensionColumns(db)
    }
}
