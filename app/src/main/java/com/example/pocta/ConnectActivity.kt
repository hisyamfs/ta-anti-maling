package com.example.pocta

import android.annotation.TargetApi
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.method.ScrollingMovementMethod
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.pocta.MyBluetoothService.Companion.uuid
import com.example.pocta.databinding.ActivityConnectBinding
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher

private val pubkey = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA5kgyBmqVI5d4nb+/hbce
    rtu8OWBGXhqSqvwjdoR9YkkfWt+nRubjzEFehdVuJ5KtUKFwO8vhnWyPUHPqNha0
    fw1aR53k/UtniyDG35/pwpSGfOttPyTDLqb+xvbggwkicI63eeQBzfXpI26qEhcD
    jMa5om+asWNOEpdqm+2cAoHuIII179SfJPQBo72/11fAj+9TeM3unC8AHUQahkIn
    fW2Pt3MZjb7Bff+5HCJZM/gj5Qxvd87eP63eiUBq8bbcnMn5PFJpdHVQbQ9x6Wb0
    uZx3CXCmxXz3EI97KoIuLds/zU4vHCw1YUoHRyk08OpN6H6JyMD7PuXRnYjpLVMc
    4QIDAQAB
    -----END PUBLIC KEY-----""".trimIndent()

private val privkey = """
    -----BEGIN RSA PRIVATE KEY-----
    MIIEpAIBAAKCAQEA5kgyBmqVI5d4nb+/hbcertu8OWBGXhqSqvwjdoR9YkkfWt+n
    RubjzEFehdVuJ5KtUKFwO8vhnWyPUHPqNha0fw1aR53k/UtniyDG35/pwpSGfOtt
    PyTDLqb+xvbggwkicI63eeQBzfXpI26qEhcDjMa5om+asWNOEpdqm+2cAoHuIII1
    79SfJPQBo72/11fAj+9TeM3unC8AHUQahkInfW2Pt3MZjb7Bff+5HCJZM/gj5Qxv
    d87eP63eiUBq8bbcnMn5PFJpdHVQbQ9x6Wb0uZx3CXCmxXz3EI97KoIuLds/zU4v
    HCw1YUoHRyk08OpN6H6JyMD7PuXRnYjpLVMc4QIDAQABAoIBAQCUMXu381kcuXqO
    kfovk+O0BYaAqfs+zfz6+h3cRHDoEkSSV4GvuCB6rsqkd/BWmSbdz7aJVLBRfa5Q
    yPe9bSkk5jPmCK93bdIpj6NL//4QEULnGx6H1yGgYSluYyuiR/uY0c8zKs8aexlY
    ivv5fkPzkWOfLBEx/MUeY8Dgra2LUleopkHhem7tXVsUrSZkD5R/amlTZpS2/dAH
    91g0hqRoFWypiwqYdsajl+ord4y/QfOieLMf4BldAE2nnEYnXHk9vFgu8Bl2N4R/
    D4T47ghLLrwsf5/Xr9AGL9BB/SXOxrCL0HxvBTCKP0VmAxrRNeS5/1jWkklj/G6m
    et6vPEnRAoGBAPRRtoGLOwvRW5hODUwkxgFQ2R3etOlwDnf+sBA9/WRSNLRa7Pys
    KNKQvDL7RPNHeNTnC+Ezim5CIFXHGDRohIiYBGyOIxZcAuJLRpH6/jAvaalC7iuZ
    zuC9YDnTWuaaPGRivEzHV3wIuvG+PX12ZMXhF6MjUXuwxu40DhsOFLLVAoGBAPFK
    rYJwXI7UdvUQkDU+GW/0ajHaE1aJWijfBg6XvkuWI6AxGF+XyPZ1Fmz44kC3WcXN
    geDefDA/05oItWCMWWJ4TCOSTcsVk3bvmrTeTLFgLp6UhmA/nzSYhDnI2N6H1fO5
    LnmfKOR+mbVr7XpgaRNp7D2P8nnOJcSNjmqJsU/dAoGAJeQWXfjt62NIxVI1lb2O
    R932Dj/f5uROGiYRwDMc/VYSfnYrkvRQUHfJ+E4n32MSRlKe8QpBSeBPi34ZLueW
    xmhtJzjUED+s4tOx2ioHCgoQZQPQVErCXvB/3/f7fRAmlZsKgQ3Zb48bDyrl9nNK
    JbZHKDHuDTTZZVAFcAS7CRECgYA+olfv6CLeoKBQdQA6Eeigex2l2ynx6K2StnHo
    D9PB4zNUPepJxijQcQxlNSXmDrIq+nGgYaBzFd5juab7bPM28GszQKMY+HzS/td1
    486crI7tczh+e4VkLcMFDPHesfwDzCoYQAxpY8OaqG14utYLyA8e2+LhY3XCU8yI
    Mz3nsQKBgQCjiVQzAplD9q4QyMn75oPH+hNwyx1Rx6g2uyMyH6xBnwiBRCrV+GYf
    aNaJ1zBrOWKzkfhN3SIw5GwL7iXTD+gjb++iRjhglyUnt63bnrvU4H7stXyDfd/k
    95cmcIqfnYQ9/NzpHCOVLePKdnF+WPYbhLdjTuCOrOGuQHl8i95g3g==
    -----END RSA PRIVATE KEY-----""".trimIndent()

class ConnectActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConnectBinding
    private lateinit var myAddress: String
    private lateinit var myBluetoothService: MyBluetoothService
    private lateinit var myRSAKeyPair: KeyPair
    private var btDevice: BluetoothDevice? = null
    private var btAdapter: BluetoothAdapter? = null
    private val tag = "ConnectActivity"
    private lateinit var encryptedMsg: String

    companion object {
        var message: String = "Default"
        const val KEY_ALIAS = "keyaliashisyam"
        const val KEY_ALIAS_DEF = "keyaliascoba"
        const val USE_DEF_KEY = true
        const val lt = "ConnectActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_connect)

        // TODO("Cari tahu kenapa dapat tipe nullable dari getStringExtra()")
        myAddress = intent.getStringExtra(HubActivity.EXTRA_ADDRESS) ?: 0.toString()
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (myAddress != "0") {
            btDevice = btAdapter?.getRemoteDevice(myAddress)
        }
        val chatMessage = "Starting comms with device at MAC address: $myAddress"

        myBluetoothService = MyBluetoothService(this)

        binding.apply {
            connectButton.setOnClickListener { connectToDevice() }
            sendMessageButton.setOnClickListener { sendMessage() }
            disconnectButton.setOnClickListener { disconnectFromDevice() }
            encryptButton.setOnClickListener { encryptMyMessage() }
            decryptButton.setOnClickListener { decryptMyMessage() }
            chatField.text = chatMessage
            chatField.movementMethod = ScrollingMovementMethod()
        }

        if (USE_DEF_KEY) setDefaultKeyPair()
    }

    override fun onDestroy() {
        if (::myRSAKeyPair.isInitialized) removeKeyStore()
        super.onDestroy()
    }

    private fun decryptMyMessage() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        val decryptedMsg= decrypt(encryptedMsg, myRSAKeyPair.private)
        val chatUpdate = "${binding.chatField.text} \nDecrypted: $decryptedMsg"
        binding.chatField.text = chatUpdate
    }

    private fun encryptMyMessage() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        myRSAKeyPair = if (hasMarshmallow()) {
            createAsymmetricKeyPair()
            getAsymmetricKeyPair()!!
        } else {
            createAsymmetricKeyPair()
        }

        val toEncrypt = binding.userInput.text.toString()
        encryptedMsg = encrypt(toEncrypt, myRSAKeyPair.public)
        val chatUpdate = "${binding.chatField.text} \nEncrypted: $encryptedMsg"
        binding.chatField.text = chatUpdate
    }

    private fun disconnectFromDevice() {
        Toast.makeText(this, "Disconnecting.....", Toast.LENGTH_SHORT).show()
        myBluetoothService.stop()
    }

    private fun sendMessage() {
        // TODO("Ubah myBluetoothService agar mengecek pesan terkirim atau tidak")
        // TODO("Ubah sendMessage agar memberi sinyal apakah pesan terkirim atau tidak")
        // TODO("Ubah myBluetoothService agar dapat menyalurkan pesan yang diterima ke ConnectActivity")
        message = binding.userInput.text.toString()
        val bytes = message.toByteArray(Charset.defaultCharset())
        myBluetoothService.write(bytes)
        val chatUpdate = "${binding.chatField.text} \nYou: $message"
        binding.chatField.text = chatUpdate
    }

    private fun connectToDevice() {
        // TODO("Ubah myBluetoothService agar mengecek koneksi berhasil")
        // TODO("Ubah connectToDevice agar memberi pesan jika koneksi berhasil")
        Toast.makeText(this, "Connecting....", Toast.LENGTH_SHORT).show()
        Log.d(tag, "Initializing connection to device.")
        myBluetoothService.startClient(btDevice, uuid)
    }

    private fun encrypt(data: String, publicKey: Key?): String {
        val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val bytes = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun decrypt(data: String, privateKey: Key?): String {
        val cipher: Cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        val encryptedData = Base64.decode(data, Base64.DEFAULT)
        val decodedData = cipher.doFinal(encryptedData)
        return String(decodedData)
    }

    private fun createAsymmetricKeyPair(): KeyPair {
        val generator: KeyPairGenerator

        if (hasMarshmallow()) {
            generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            getKeyGenParameterSpec(generator)
        } else {
            generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048)
        }

        return generator.generateKeyPair()
    }

    @TargetApi(23)
    private fun getKeyGenParameterSpec(generator: KeyPairGenerator) {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            //.setUserAuthenticationRequired(true)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)

        generator.initialize(builder.build())
    }

    private fun getAsymmetricKeyPair(): KeyPair? {
        val keyStore: KeyStore = createKeyStore()

        val alias: String = if (USE_DEF_KEY) {
            KEY_ALIAS_DEF
        } else {
            KEY_ALIAS
        }

        val privateKey = keyStore.getKey(alias, null) as PrivateKey?
        val publicKey = keyStore.getCertificate(alias)?.publicKey

        return if (privateKey != null && publicKey != null) {
            KeyPair(publicKey, privateKey)
        } else {
            null
        }
    }

    private fun setDefaultKeyPair() {
        val keyStore: KeyStore = createKeyStore()

        Log.i(lt, "ConnectActivity: accessing default keypair.")
        val privateKey = keyStore.getKey(KEY_ALIAS_DEF, null) as PrivateKey?
        val publicKey = keyStore.getCertificate(KEY_ALIAS_DEF)?.publicKey

        if (privateKey == null && publicKey == null) {
            Log.i(lt, "ConnectActivity: no default keypair found. generating default keypair.")
            val privkeystring = privkey.replace("\n", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
            val pubkeystring = pubkey.replace("\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")

            try {
                val pk: PrivateKey = KeyFactory.getInstance("RSA").generatePrivate(
                    PKCS8EncodedKeySpec(Base64.decode(privkeystring, Base64.DEFAULT))
                )
                val cert: Certificate = CertificateFactory.getInstance("X.509").generateCertificate(
                    ByteArrayInputStream(Base64.decode(pubkeystring, Base64.DEFAULT))
                )

                Log.i(lt, "ConnectActivity: loading default keypair.")
                val ks = KeyStore.getInstance("AndroidKeyStore")
                val certArray: Array<Certificate> = arrayOf(cert)
                ks.load(null)
                ks.setKeyEntry(KEY_ALIAS_DEF, pk, null, certArray)
            } catch (e: KeyStoreException) {
                Log.e(lt, "ConnectActivity: error while loading keypair", e)
            } catch (e2: InvalidKeySpecException) {
                Log.e(lt, "ConnectActivity: invalid key spec", e2)
            }
        }
    }

    private fun removeKeyStore() = createKeyStore().deleteEntry(KEY_ALIAS)

    private fun createKeyStore(): KeyStore {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        return keyStore
    }
}

fun hasMarshmallow() : Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
}
