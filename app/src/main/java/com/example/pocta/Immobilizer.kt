package com.example.pocta

import android.bluetooth.BluetoothDevice
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.PublicKey
import javax.crypto.SecretKey

const val TABLE_IMMOBILIZER = "table_immobilizer_blob"
const val ID = "id"
const val ADDRESS = "address"
const val NAME = "name"
const val KEY = "key"

@Entity(tableName = TABLE_IMMOBILIZER)
data class Immobilizer(
    @PrimaryKey val address: String,
    @ColumnInfo(name = NAME) val name: String,
    @ColumnInfo(name = KEY) val key: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Immobilizer

        if (address != other.address) return false
        if (name != other.name) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = address.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + key.contentHashCode()
        return result
    }
}

data class UnregisteredImmobilizer(
    val address: String,
    val name: String
) {
    constructor(device: BluetoothDevice) : this(device.address, device.name)
}

data class ActiveImmobilizer(
    var name: String,
    var status: String
)

data class ImmobilizerConnection(
    val address: String,
    val name: String,
    val sk: SecretKey,
    val pk: PublicKey
)

data class UserPrompt(
    val promptType: Int,
    val promptMessage: String,
    val showPrompt: Boolean
)

/**
 * Enumeration of Immobilizer's Request Codes
 * @param reqString String of the request code
 */
enum class ImmobilizerUserRequest(val reqString: String) {
    NOTHING("!0"),
    UNLOCK("!1"),
    CHANGE_PIN("!2"),
    REGISTER_PHONE("!3"),
    REMOVE_PHONE("!4"),
    DISABLE("!5")
}