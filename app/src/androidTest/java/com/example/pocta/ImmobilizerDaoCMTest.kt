package com.example.pocta

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import javax.crypto.spec.SecretKeySpec

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@SmallTest
class ImmobilizerDaoCMTest {
    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var dao: ImmobilizerDao
    private lateinit var db: ImmobilizerDatabase
    private lateinit var cm: UserCredentialManager

    companion object {
        fun getAddress(immobilizer: Immobilizer?): String = immobilizer?.address ?: "0"
        fun getName(immobilizer: Immobilizer?): String = immobilizer?.name ?: "0"
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ImmobilizerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.immobilizerDao()
        cm = UserCredentialManager(context)
    }

    @After
    @Throws(IOException::class)
    fun teardown() {
        db.close()
    }

    @Test
    fun testDBFunction() = runBlocking {
        val sk = ByteArray(16) { 0 }
        val sk2: ByteArray = sk.map { (it + 1).toByte() }.toByteArray()

        val immoA = Immobilizer("1", "A", sk)
        val immoB = Immobilizer("2", "B", sk2)
        val immoC = Immobilizer("3", "C", sk)
//        val rList = listOf(immoA, immoB, immoC)
        dao.addImmobilizer(immoA, immoB, immoC)
        val list = dao.getImmobilizerList().getOrAwaitValue()
        assertEquals("Length error", 3, list.size)
        assertThat("List items", list, contains(immoA, immoB, immoC))
    }

    @Test
    fun testKeyEncryption() = runBlocking {
        val skKeyString = "abcdefghijklmnop".toByteArray()
        val sk = SecretKeySpec(skKeyString, 0, skKeyString.size, "AES")
        val kp = cm.getDefaultRSAKeyPair()
        val encryptedKey = cm.encryptSecretKey(sk, kp.public)
        assertThat(encryptedKey, notNullValue())
        dao.addImmobilizer(
            Immobilizer("1", "A", encryptedKey ?: "0".toByteArray())
        )
        val immobilizer = dao.getImmobilizer("1")
        val imSK = immobilizer?.key ?: "0".toByteArray()

        assertThat("Key storage", imSK, equalTo(encryptedKey))

        val decryptedKey = cm.decryptSecretKey(encryptedKey, kp.private)
        assertThat("Key decryption", decryptedKey.encoded, equalTo(sk.encoded))
    }
}


