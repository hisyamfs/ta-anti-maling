package com.example.pocta

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.db.rowParser

@Parcelize
data class Immobilizer(
    val id: Long?,
    val address: String,
    val name: String,
    val key: ByteArray
) : Parcelable {
    companion object {
        const val TABLE_IMMOBILIZER = "table_immobilizer_blob"
        const val ID = "id"
        const val ADDRESS = "address"
        const val NAME = "name"
        const val KEY = "key"
    }

    override fun toString(): String = "$name\n$address"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Immobilizer

        if (id != other.id) return false
        if (address != other.address) return false
        if (name != other.name) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + address.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + key.contentHashCode()
        return result
    }
}

val immobilizerParser = rowParser{id: Long, address: String, name: String, key: ByteArray ->
    Immobilizer(id, address, name, key)
}