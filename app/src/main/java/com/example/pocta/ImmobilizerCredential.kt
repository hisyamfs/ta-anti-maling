package com.example.pocta

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.db.*

const val IMMOBILIZER_DB = "database_immobilizer.db"

@Parcelize
data class Immobilizer(
    val id: Long?,
    val address: String,
    val name: String,
    val key: String
) : Parcelable {
    companion object {
        const val TABLE_IMMOBILIZER = "table_immobilizer"
        const val ID = "id"
        const val ADDRESS = "address"
        const val NAME = "name"
        const val KEY = "key"
    }
}

class ImmobilizerDBHelper(context: Context) : ManagedSQLiteOpenHelper(context, IMMOBILIZER_DB, null, 1) {
    companion object { // Singleton pattern
        private var instance: ImmobilizerDBHelper? = null
        @Synchronized
        fun getInstance(ctx: Context): ImmobilizerDBHelper {
            if (instance == null) {
                instance = ImmobilizerDBHelper(ctx.applicationContext)
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        // Create DB table
        db?.createTable(
            Immobilizer.TABLE_IMMOBILIZER,
            true,
            Immobilizer.ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
            Immobilizer.ADDRESS to TEXT + UNIQUE + NOT_NULL,
            Immobilizer.NAME to TEXT + NOT_NULL,
            Immobilizer.KEY to TEXT + NOT_NULL
        )
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.dropTable(Immobilizer.TABLE_IMMOBILIZER, true)
    }
}

val Context.database: ImmobilizerDBHelper
    get() = ImmobilizerDBHelper.getInstance(this)