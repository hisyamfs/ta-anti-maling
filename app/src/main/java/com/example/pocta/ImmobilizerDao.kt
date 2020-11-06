package com.example.pocta

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ImmobilizerDao {
    @Query("SELECT * FROM table_immobilizer_blob")
    fun getImmobilizerList(): LiveData<List<Immobilizer>>

    @Query("SELECT address FROM table_immobilizer_blob")
    fun getAddressList(): LiveData<List<String>>

    @Query("SELECT * FROM table_immobilizer_blob WHERE address = :qAddress LIMIT 1")
    suspend fun getImmobilizer(qAddress: String): Immobilizer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addImmobilizer(vararg immobilizer: Immobilizer)

    @Query("UPDATE table_immobilizer_blob SET name = :qNewName WHERE address = :qAddress")
    suspend fun renameImmobilizer(qAddress: String, qNewName: String)

    @Delete
    suspend fun deleteImmobilizer(immobilizer: Immobilizer)

    @Query("DELETE FROM table_immobilizer_blob WHERE address = :qAddress")
    suspend fun deleteImmobilizer(qAddress: String)

    @Query("DELETE FROM table_immobilizer_blob")
    suspend fun deleteAll()
}