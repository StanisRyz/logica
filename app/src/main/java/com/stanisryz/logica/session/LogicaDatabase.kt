package com.stanisryz.logica.session

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [GameSessionEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class LogicaDatabase : RoomDatabase() {
    abstract fun gameSessionDao(): GameSessionDao

    companion object {
        private const val DATABASE_NAME = "logica.db"

        fun create(context: Context): LogicaDatabase {
            val applicationContext = context.applicationContext
            return Room
                .databaseBuilder<LogicaDatabase>(
                    context = applicationContext,
                    name = applicationContext.getDatabasePath(DATABASE_NAME).absolutePath,
                ).setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}
