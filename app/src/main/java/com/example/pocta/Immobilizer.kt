package com.example.pocta

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Immobilizer(
    val id: Long?,
    val address: String,
    val name: String,
    val key: String
) : Parcelable {
    override fun toString(): String = "$name\n$address"
    companion object {
        const val TABLE_IMMOBILIZER = "table_immobilizer2"
        const val ID = "id"
        const val ADDRESS = "address"
        const val NAME = "name"
        const val KEY = "key"
    }
}