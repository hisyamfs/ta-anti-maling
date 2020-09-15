package com.example.pocta

import android.content.Context
import androidx.room.*

@Entity(tableName = "immobilizer_table")
data class Immobilizer(
    @PrimaryKey @ColumnInfo(name = "mac_address") val address: String,
    @ColumnInfo(name = "immobilizer_name") val name: String,
    @ColumnInfo(name = "cipher_key") var cipherKey: String,
    @ColumnInfo(name = "usage_count") val usageCount: Int,
    @ColumnInfo(name = "last_usage") val lastUsage: String
)

@Dao
interface ImmobilizerDAO {
    @Query("SELECT * FROM immobilizer_table")
    fun getAll(): List<Immobilizer>

    @Query("SELECT * FROM immobilizer_table WHERE mac_address=:qAddress LIMIT 1")
    fun getByAddress(qAddress: String): Immobilizer

    @Query("SELECT * FROM immobilizer_table WHERE immobilizer_name=:qName LIMIT 1")
    fun getByName(qName: String): Immobilizer

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(newImmobilizer: Immobilizer)

    @Update
    fun update(vararg immobilizer: Immobilizer)

    @Delete
    fun deleteImmobilizer(immobilizer: Immobilizer)
}

@Database(
    entities = [Immobilizer::class],
    version = 1,
    exportSchema = false
)
abstract class ImmobilizerDatabase: RoomDatabase() {
    abstract fun immobilizerDao(): ImmobilizerDAO
    companion object {
        @Volatile
        private var INSTANCE: ImmobilizerDatabase? = null

        fun getDatabase(context: Context): ImmobilizerDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ImmobilizerDatabase::class.java,
                    "word_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}



