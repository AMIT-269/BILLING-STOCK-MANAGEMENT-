package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.Bill
import com.example.data.model.JewellerAccount
import com.example.data.model.JewellerSettings
import com.example.data.model.StockTransaction

@Database(
    entities = [
        JewellerAccount::class,
        JewellerSettings::class,
        Bill::class,
        StockTransaction::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): JewellerAccountDao
    abstract fun settingsDao(): JewellerSettingsDao
    abstract fun billDao(): BillDao
    abstract fun stockTransactionDao(): StockTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun ensureGstColumn(database: SupportSQLiteDatabase) {
            try {
                val cursor = database.query("PRAGMA table_info(jeweller_accounts)")
                var hasGst = false
                while (cursor.moveToNext()) {
                    val colIndex = cursor.getColumnIndex("name")
                    if (colIndex >= 0 && cursor.getString(colIndex).equals("gstNumber", ignoreCase = true)) {
                        hasGst = true
                        break
                    }
                }
                cursor.close()
                if (!hasGst) {
                    database.execSQL("ALTER TABLE jeweller_accounts ADD COLUMN gstNumber TEXT NOT NULL DEFAULT ''")
                }
            } catch (_: Exception) {}
        }

        private fun ensurePaymentsJsonColumn(database: SupportSQLiteDatabase) {
            try {
                val cursor = database.query("PRAGMA table_info(bills)")
                var hasPaymentsJson = false
                while (cursor.moveToNext()) {
                    val colIndex = cursor.getColumnIndex("name")
                    if (colIndex >= 0 && cursor.getString(colIndex).equals("paymentsJson", ignoreCase = true)) {
                        hasPaymentsJson = true
                        break
                    }
                }
                cursor.close()
                if (!hasPaymentsJson) {
                    database.execSQL("ALTER TABLE bills ADD COLUMN paymentsJson TEXT NOT NULL DEFAULT '[]'")
                }
            } catch (_: Exception) {}
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureGstColumn(database)
                ensurePaymentsJsonColumn(database)
            }
        }

        val MIGRATION_4_6 = object : Migration(4, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureGstColumn(database)
                ensurePaymentsJsonColumn(database)
            }
        }

        val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureGstColumn(database)
                ensurePaymentsJsonColumn(database)
            }
        }

        val MIGRATION_1_6 = object : Migration(1, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                ensureGstColumn(database)
                ensurePaymentsJsonColumn(database)
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "jewellery_billing_database"
                )
                    .addMigrations(MIGRATION_1_6, MIGRATION_3_6, MIGRATION_4_6, MIGRATION_5_6)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            ensureGstColumn(db)
                            ensurePaymentsJsonColumn(db)
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
