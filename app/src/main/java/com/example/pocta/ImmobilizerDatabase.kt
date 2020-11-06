@file:Suppress("SpellCheckingInspection")

package com.example.pocta

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

const val DB_NAME = "AntImmo_database"

@Database(entities = [Immobilizer::class], version = 1, exportSchema = false)
abstract class ImmobilizerDatabase: RoomDatabase() {
    abstract fun immobilizerDao(): ImmobilizerDao
    companion object {
        @Volatile
        private var INSTANCE: ImmobilizerDatabase? = null

        fun getDatabase(context: Context): ImmobilizerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ImmobilizerDatabase::class.java,
                    DB_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}