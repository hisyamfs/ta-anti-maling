package com.example.pocta

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.SecretKey

class MyBluetoothService(context: Context, handler: Handler) {
    private var btAdapter: BluetoothAdapter? = null
    private var btAcceptThread: AcceptThread? = null
    private var btConnectThread: ConnectThread? = null
    private var btConnectedThread: ConnectedThread? = null
    private var btDevice: BluetoothDevice? = null
    private var btState: Int = 0
    private var btNewState: Int = 0
    private var btContext: Context? = null
    private var btHandler: Handler? = null
    private var btEncryptionKey: SecretKey? = null
    // TODO("Buat agar perubahan nilai useOutputEncryption dan useInputDecryption di-pass ke handler.")
    var useOutputEncryption = false
    var useInputDecryption = false
    private val TAG = "MY_BLUETOOTH_SERVICE"

    companion object {
        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val STATE_NONE = 0
        const val STATE_LISTEN = 1
        const val STATE_CONNECTING = 2
        const val STATE_CONNECTED = 3
        // Defines several constants used when transmitting messages between the
        // service and the UI.
        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_TOAST: Int = 2
        const val MESSAGE_DEVICE_NAME: Int = 3
    }

    init {
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        btState = STATE_NONE
        btNewState = btState
        btContext = context
        btHandler = handler
    }

    @Synchronized
    fun start() {
        Log.d(TAG, "start")
        // Cancel running threads
        stop()
        // Start the thread to listen on a BluetoothSocket
        if (btAcceptThread == null) {
            btAcceptThread = AcceptThread()
            btAcceptThread?.start()
        }
    }

    fun startClient(device: BluetoothDevice?, uuid: UUID) {
        Log.d(TAG, "start client: started")
        // Cancel any running threads
        stop()
        // connect
        btConnectThread = ConnectThread(device, uuid)
        btConnectThread?.start()
    }

    fun connected(socket: BluetoothSocket?, device: BluetoothDevice?) {
        Log.d(TAG, "connected: starting....")
        // Start the thread
        btConnectedThread = ConnectedThread(socket)
        btConnectedThread?.start()
    }

    fun stop() {
        // Cancel any thread
        if (btConnectThread != null) {
            btConnectThread?.cancel()
            btConnectThread = null
        }
        if (btConnectedThread != null) {
            btConnectedThread?.cancel()
            btConnectedThread = null
        }

        if (btAcceptThread != null) {
            btAcceptThread?.cancel()
            btAcceptThread = null
        }
        btState = STATE_NONE
    }

    fun write(bytes: ByteArray) {
        Log.d(TAG, "write: write called.")
        btConnectedThread?.write(bytes)
    }

    fun setAESKey(key: SecretKey) {
        btEncryptionKey = key
    }

    @SuppressLint("GetInstance")
    fun btEncrypt(bytes: ByteArray): ByteArray {
        if (btEncryptionKey != null) {
            // pad input with nulls if needed
            val final: ByteArray
            if (bytes.size % 16 != 0) {
                Log.i(TAG, "Padding input bytes")
                val paddedSize = bytes.size / 16 + 16
                final = ByteArray(paddedSize)
                for (i in final.indices) {
                    if (i < bytes.size) {
                        final[i] = bytes[i]
                    } else {
                        final[i] = 0
                    }
                }
            } else {
                final = bytes
            }
            return try {
                val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, btEncryptionKey)
                cipher.doFinal(final)
            } catch (e: BadPaddingException) {
                Log.e(TAG, "decrypt(): Padding error", e)
                "decrypt(): Padding Error!".toByteArray(Charset.defaultCharset())
            } catch (e: IllegalBlockSizeException) {
                Log.e(TAG, "decrypt(): block size error", e)
                "decrypt(): Block Size Error!".toByteArray(Charset.defaultCharset())
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "decrypt(): bad base64 error", e)
                "decrypt(): Bad base 64!".toByteArray(Charset.defaultCharset())
            }
        } else return "Encryption Key not Found".toByteArray()
    }

    @SuppressLint("GetInstance")
    fun btDecrypt(bytes: ByteArray): ByteArray {
        if (btEncryptionKey != null) {
            // pad input with nulls if needed
            val final: ByteArray
            if (bytes.size % 16 != 0) {
                Log.i(TAG, "Padding input bytes")
                val paddedSize = bytes.size / 16 + 16
                final = ByteArray(paddedSize)
                for (i in final.indices) {
                    if (i < bytes.size) {
                        final[i] = bytes[i]
                    } else {
                        final[i] = 0
                    }
                }
            } else {
                final = bytes
            }

            return try {
                val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, btEncryptionKey)
                cipher.doFinal(final)
            } catch (e: BadPaddingException) {
                Log.e(TAG, "decrypt(): Padding error", e)
                "decrypt(): Padding Error!".toByteArray(Charset.defaultCharset())
            } catch (e: IllegalBlockSizeException) {
                Log.e(TAG, "decrypt(): block size error", e)
                "decrypt(): Block Size Error!".toByteArray(Charset.defaultCharset())
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "decrypt(): bad base64 error", e)
                "decrypt(): Bad base 64!".toByteArray(Charset.defaultCharset())
            }
        } else return "Decryption Key not Found".toByteArray()
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket? = null
            try {
                tmp = btAdapter?.listenUsingRfcommWithServiceRecord("SECURE_CHAT", uuid)
                Log.d(TAG, "Accept thread: setting up server using: $uuid")
            } catch (e: IOException) {
                Log.e(TAG, "Accept thread: IOException", e)
            }
            mmServerSocket = tmp
            btState = STATE_LISTEN
        }

        override fun run() {
            Log.d(TAG, "Accept thread: running")
            var socket: BluetoothSocket? = null

            try {
                Log.d(TAG, "Accept thread: run : RFCOMM Server Socket starting.")
                socket = mmServerSocket?.accept()
                Log.d(TAG, "Accept thread: run : RFCOMM Server Socket accepted connection.")
            } catch (e: IOException) {
                Log.e(TAG, "Accept thread: IOException", e)
            }

            if (socket != null) {
                connected(socket, btDevice)
            }

            Log.i(TAG, "Accept thread: end run.")
        }

        fun cancel() {
            Log.d(TAG, "Accept thread: cancelling.")
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Accept thread: cancelling failed due to IOException", e)
            }
        }
    }

    private inner class ConnectThread(device: BluetoothDevice?, myUUID: UUID) : Thread() {
        private var mmSocket: BluetoothSocket? = null
        private var tUUID: UUID? = null

        init {
            Log.d(
                TAG,
                "connect thread: started, connecting to ${device?.name} at ${device?.address}."
            )
            btDevice = device
            tUUID = myUUID

            // Create RFCOMMSOCKET
            var tmp: BluetoothSocket? = null
            try {
                Log.d(TAG, "connect thread: trying to create rfcommsocket with uuid : $tUUID")
                tmp = btDevice?.createRfcommSocketToServiceRecord(tUUID)
            } catch (e: IOException) {
                Log.e(TAG, "connect thread: couldn't create rfcommsocket ", e)
            }
            mmSocket = tmp
        }

        override fun run() {
            Log.i(TAG, "connect thread: run.")
            btAdapter?.cancelDiscovery()

            try {
                mmSocket?.connect()
                Log.d(TAG, "connect thread: run: connected.")
                connected(mmSocket, btDevice)
            } catch (e: IOException) {
                try {
                    mmSocket?.close()
                    Log.d(TAG, "connect thread: run: closed socket.")
                } catch (e2: IOException) {
                    Log.e(TAG, "connect thread: run: unable to close socket.", e)
                }
                Log.d(TAG, "connect thread: run: couldn't connect to uuid: $tUUID")
            }
        }

        fun cancel() {
            try {
                Log.d(TAG, "connect thread: cancel: closing client socket.")
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "connect thread: cancel: failed to close socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket?) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?

        init {
            Log.d(TAG, "connected thread: starting.")

            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null

            try {
                tmpIn = mmSocket?.inputStream
                tmpOut = mmSocket?.outputStream
            } catch (e: IOException) {
                e.printStackTrace()
            }

            mmInStream = tmpIn
            mmOutStream = tmpOut

            btState = STATE_CONNECTED
        }

        // TODO("Ubah fungsi btDecrypt() agar hanya menerima sebagian mmBuffer saja, yaitu sebanyak byte yang diterima")
        override fun run() {
            var numBytes: Int
            val mmBuffer = ByteArray(256)

            while (true) {
                try {
                    if (mmInStream != null) {
                        numBytes = mmInStream.read(mmBuffer)
                        val inBytes: ByteArray = mmBuffer.copyOfRange(0, numBytes)
                        Log.d(TAG, "connected thread: receiving $numBytes bytes")
                        var hexString = ""
                        for (i in inBytes.indices) {
                            val hex = if (i % 16 == 0) {
                                String.format("\n%02X ", inBytes[i])
                            } else {
                                String.format("%02X ", inBytes[i])
                            }
                            hexString = "$hexString $hex"
                        }
                        Log.d(TAG, "connected thread: input stream: $hexString")
                        val final =
                            if (useInputDecryption) btDecrypt(inBytes)
                            else inBytes
                        numBytes = final.size
                        btHandler?.obtainMessage(MESSAGE_READ, numBytes, -1, final)
                            ?.sendToTarget()
                    } else {
                        Log.d(TAG, "mmInStream is null")
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "connected thread: error reading in stream", e)
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            val sentBytes: ByteArray = if (useOutputEncryption) btEncrypt(bytes) else bytes
            // encode in base64 for UI activity
            val uiBytes: ByteArray =
                if (useOutputEncryption) Base64.encode(sentBytes, Base64.DEFAULT) else sentBytes
            val text = String(sentBytes)
            Log.d(TAG, "connect thread: write: $text")
            try {
                mmOutStream?.write(sentBytes)
                // Share the sent message back to UI activity
                btHandler?.obtainMessage(MESSAGE_WRITE, -1, -1, uiBytes)
                    ?.sendToTarget()
            } catch (e: IOException) {
                Log.e(TAG, "connect thread: write: IOException", e)
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "connect thread: cancel: IOException", e)
            }
        }
    }
}
