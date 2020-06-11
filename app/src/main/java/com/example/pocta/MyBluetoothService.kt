package com.example.pocta

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*

class MyBluetoothService(context: Context) {
    private var btAdapter: BluetoothAdapter? = null
    private var btAcceptThread: AcceptThread? = null
    private var btConnectThread: ConnectThread? = null
    private var btConnectedThread: ConnectedThread? = null
    private var btDevice: BluetoothDevice? = null
    private var btState : Int = 0
    private var btNewState : Int = 0
    private var btContext: Context? = null
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
    }

    @Synchronized fun start() {
        Log.d(TAG, "start")
        // Cancel running threads
        _stop()
        // Start the thread to listen on a BluetoothSocket
        if (btAcceptThread == null) {
            btAcceptThread = AcceptThread()
            btAcceptThread?.start()
        }
    }

    @Synchronized fun startClient(device: BluetoothDevice?, uuid: UUID) {
        Log.d(TAG, "start client: started")
        // Cancel any running threads
        _stop()
        // Start connect thread
        btConnectThread = ConnectThread(device, uuid)
        btConnectThread?.start()
    }

    @Synchronized fun connected(socket: BluetoothSocket?, device: BluetoothDevice?) {
        Log.d(TAG, "connected: starting....")
        // Cancel any running thread
        _stop()
        // Start connected thread
        btConnectedThread = ConnectedThread(socket)
        btConnectedThread?.start()
    }

    private fun _stop() {
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

    @Synchronized fun stop() {
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

    fun write(out: ByteArray) {
        Log.d(TAG, "write: write called.")
        btConnectedThread?.write(out)
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket?

        init {
            var tmp: BluetoothServerSocket?  = null
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
            Log.d(TAG, "connect thread: started, connecting to ${device?.name} at ${device?.address}.")
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

        override fun run() {
            var numBytes: Int
            val mmBuffer = ByteArray(1024)

            while (true) {
                try {
                    numBytes = mmInStream?.read(mmBuffer) ?: 0
                    val incomingMessage = mmBuffer.toString(Charset.defaultCharset())
                    Log.d(TAG, "connected thread: input stream: $incomingMessage")
                } catch (e: IOException) {
                    Log.e(TAG, "connected thread: error reading in stream", e)
                    break
                }
            }
        }

        fun write(bytes: ByteArray) {
            val text = String(bytes)
            Log.d(TAG, "connect thread: write: $text")
            try {
                mmOutStream?.write(bytes)
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
