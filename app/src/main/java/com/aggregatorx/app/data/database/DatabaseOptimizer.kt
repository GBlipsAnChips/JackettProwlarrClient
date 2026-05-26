package com.aggregatorx.app.data.database

import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

object DatabaseOptimizer {

    fun configureDatabase(builder: RoomDatabase.Builder<*>): RoomDatabase.Builder<*> {
        return builder
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setQueryExecutor(Executors.newFixedThreadPool(2))
            .enableMultiInstanceInvalidation()
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA journal_size_limit = 16384000")
                    db.execSQL("PRAGMA cache_size = -64000")
                    db.execSQL("PRAGMA temp_store = MEMORY")
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    db.execSQL("PRAGMA mmap_size = 30000000")
                }
            })
    }
}
