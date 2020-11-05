package com.example.pocta

import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import java.security.KeyPair
import javax.crypto.SecretKey

const val TAG = "ImmobilizerRepository"

/**
 * Immobilizer repository, all-in-one stop registered immobilizer and user credentials management
 * class
 */
class ImmobilizerRepository(
    private val dao: ImmobilizerDao,
    private val cm: UserCredentialManager
) {
    val immobilizerList: LiveData<List<Immobilizer>> = dao.getImmobilizerList()
    val userRSAKey: KeyPair = cm.getStoredRSAKeyPair() ?: cm.getDefaultRSAKeyPair()
    val defaultKey: SecretKey = cm.getDefaultSymmetricKey()

    fun addImmobilizer(immobilizer: Immobilizer) {
        GlobalScope.launch(Dispatchers.IO) {
            dao.addImmobilizer(immobilizer)
        }
    }

    fun addImmobilizer(qAddress: String, qName: String, qKey: SecretKey) {
        GlobalScope.launch(Dispatchers.IO) {
            val encryptedKey = cm.encryptSecretKey(qKey, userRSAKey.public)
            encryptedKey?.let {
                dao.addImmobilizer(Immobilizer(qAddress, qName, encryptedKey))
            }
        }
    }

    suspend fun getImmobilizer(qAddress: String): Immobilizer? {
        return withContext(Dispatchers.IO) {
            dao.getImmobilizer(qAddress)
        }
    }

    fun getStoredKey(qAddress: String): SecretKey {
        return runBlocking {
            val encryptedKey = getImmobilizer(qAddress)?.key
            cm.decryptSecretKey(encryptedKey, userRSAKey.private)
        }
    }

    fun renameImmobilizer(qAddress: String, qNewName: String) {
        GlobalScope.launch(Dispatchers.IO) {
            dao.renameImmobilizer(qAddress, qNewName)
        }
    }

    fun deleteImmobilizer(immobilizer: Immobilizer) {
        GlobalScope.launch(Dispatchers.IO) { dao.deleteImmobilizer(immobilizer) }
    }

    fun deleteImmobilizer(qAddress: String) {
        GlobalScope.launch(Dispatchers.IO) { dao.deleteImmobilizer(qAddress) }
    }

    fun deleteAll() {
        GlobalScope.launch(Dispatchers.IO) { dao.deleteAll() }
    }
}


