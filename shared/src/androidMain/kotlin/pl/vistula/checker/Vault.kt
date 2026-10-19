package pl.vistula.checker

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Tokens, snapshots and history are encrypted with a non-exportable Android Keystore key. */
class Vault(context: Context) : VaultStore {
    private val file: AtomicFile = AtomicFile(File(context.noBackupFilesDir, "state.bin"))
    override val storePath: String = context.noBackupFilesDir.absolutePath

    @Throws(Exception::class)
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore")
        store.load(null)
        if (!store.containsAlias(ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generator.generateKey()
        }
        return store.getKey(ALIAS, null) as SecretKey
    }

    @Throws(Exception::class)
    override fun read(): JSONObject {
        val bytes = try {
            file.readFully()
        } catch (e: FileNotFoundException) {
            // Let AtomicFile recover its legacy backup before deciding this is a new store.
            val base = file.baseFile
            if (!base.exists() && !File(base.path + ".bak").exists()) return JSONObject()
            throw e
        }
        if (bytes.size < 29 || bytes[0].toInt() != 1) {
            throw IOException("Nie można odczytać zapisanych danych.")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 1, 12))
        return JSONObject(String(cipher.doFinal(bytes, 13, bytes.size - 13), StandardCharsets.UTF_8))
    }

    @Throws(Exception::class)
    override fun write(state: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(state.toString().toByteArray(StandardCharsets.UTF_8))
        val stream = file.startWrite()
        try {
            stream.write(1)
            stream.write(cipher.iv)
            stream.write(encrypted)
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
    }

    override fun clear() {
        file.delete()
    }

    companion object {
        private const val ALIAS = "vistula_checker_local_v1"
    }
}

/** SharedPreferences-backed KeyValueStore for Android preserving existing preference files. */
class AndroidKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long =
        prefs.getLong(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

