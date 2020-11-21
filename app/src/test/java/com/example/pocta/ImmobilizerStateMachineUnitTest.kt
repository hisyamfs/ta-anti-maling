package com.example.pocta

import android.bluetooth.BluetoothDevice
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.mockito.runners.MockitoJUnitRunner
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@RunWith(MockitoJUnitRunner::class)
class ImmobilizerStateMachineUnitTest {
    var immobilizerIOMock: ImmobilizerStateMachineIO? = null
    var device: BluetoothDevice? = null
    var immobilizerSM: ImmobilizerStateMachine? = null
    companion object {
        fun getDefaultKey(): SecretKey {
            val byteArray = ByteArray(16) { 1 }
            return SecretKeySpec(byteArray, 0, 16, "AES")
        }
    }

    @Before
    fun setup() {
        immobilizerIOMock = Mockito.mock(ImmobilizerStateMachineIO::class.java)
        device = Mockito.mock(BluetoothDevice::class.java)
        immobilizerSM = ImmobilizerStateMachine(immobilizerIOMock!!)
    }

    @Test
    fun testSMUnlockRequest() {
        val secretKey = getDefaultKey()
//        `when`(device!!.name).thenReturn("testImmobilizer")
//        `when`(device!!.address).thenReturn("00:00:00:00:00:00")
        immobilizerSM!!.initConnection(
            device!!, ImmobilizerUserRequest.UNLOCK, secretKey, "test"
        )
        // DC state
        verify(immobilizerIOMock)!!.setAESKey(secretKey)
        verify(immobilizerIOMock)!!.startBtClient(device!!, IMMO_UUID)
        immobilizerSM!!.onBTConnection()
        // Connect state
        val ack = immobilizerSM!!.ACK.toByteArray()
        verify(immobilizerIOMock)!!
            .updateStatus(, "Connecting")
        verify(immobilizerIOMock)!!
            .writeBt(ack, false)
        immobilizerSM!!.onBTInput(ack, ack.size)
        // Request state
        verify(immobilizerIOMock)!!
            .writeBt(ImmobilizerUserRequest.UNLOCK.reqString.toByteArray(), encrypted = false)
        immobilizerSM!!.onBTInput(ack, ack.size)
        // Challenge state
        val nonce: ByteArray = secretKey.encoded
        immobilizerSM!!.onBTInput(nonce, nonce.size)
        verify(immobilizerIOMock)!!
            .writeBt(nonce, encrypted = true)
        // Response state
        immobilizerSM!!.onBTInput(ack, ack.size)
        verify(immobilizerIOMock)!!
            .promptUser(
                ImmobilizerIOEvent.MESSAGE_PROMPT_PIN.code, "Masukkan PIN anda!"
            )
        immobilizerSM!!.onUserInput("1234".toByteArray())
        verify(immobilizerIOMock)!!.writeBt("1234".toByteArray(), encrypted = true)
        // Pin state
        immobilizerSM!!.onBTInput(ack, ack.size)
        // Unlock state
        immobilizerSM!!.onUserRequest(ImmobilizerUserRequest.UNLOCK)
        immobilizerSM!!.onBTInput(ack, ack.size)
        // Request state
        immobilizerSM!!.onBTDisconnect()
        // Disconnect state
        verify(immobilizerIOMock)!!.updateStatus(, "Disconnected")
    }

    @After
    fun teardown() {
        immobilizerIOMock = null
        device = null
        immobilizerSM = null
    }
}