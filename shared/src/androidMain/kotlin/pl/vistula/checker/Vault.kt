package pl.vistula.checker

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
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
    private fun key(): SecretKey = synchronized(KEY_LOCK) {
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
        store.getKey(ALIAS, null) as SecretKey
    }

    private val cache: StateCache = CACHES.computeIfAbsent(storePath) { StateCache() }

    override fun stamp(): Long = StateCache.stampOf(file.baseFile.lastModified(), file.baseFile.length())

    @Throws(Exception::class)
    override fun read(): JSONObject {
        val before = stamp()
        cache.get(before)?.let { return it }
        val state = decrypt()
        // Keyed by the stamp seen BEFORE decrypting: a write that lands meanwhile makes this entry a miss, never stale.
        cache.put(before, state)
        return state
    }

    @Throws(Exception::class)
    private fun decrypt(): JSONObject {
        val bytes = try {
            file.readFully()
        } catch (e: FileNotFoundException) {
            // Let AtomicFile recover its legacy backup before deciding this is a new store.
            val base = file.baseFile
            if (!base.exists() && !File(base.path + ".bak").exists()) return JSONObject()
            throw e
        }
        // A truncated file, a wrong key (lost or replaced Keystore entry) and damaged content cannot heal by retrying.
        if (bytes.size < 29 || bytes[0].toInt() != 1) throw VaultUnreadableException()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 1, 12))
            return JSONObject(String(cipher.doFinal(bytes, 13, bytes.size - 13), StandardCharsets.UTF_8))
        } catch (e: javax.crypto.AEADBadTagException) {
            throw VaultUnreadableException(e)
        } catch (e: java.security.InvalidKeyException) {
            throw VaultUnreadableException(e)
        } catch (e: java.security.UnrecoverableKeyException) {
            throw VaultUnreadableException(e)
        } catch (e: org.json.JSONException) {
            throw VaultUnreadableException(e)
        }
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
            cache.invalidate()
            throw e
        }
        cache.put(stamp(), state)
    }

    override fun clear() {
        file.delete()
        cache.invalidate()
        // The Keystore key stays: one alias serves every Vault directory of the app, so deleting it here would make
        // another store unreadable. Without the deleted file the key decrypts nothing.
    }

    companion object {
        private const val ALIAS = "vistula_checker_local_v1"
        private val KEY_LOCK = Any()

        /** One cache per state directory, shared by every Vault instance in the process. */
        private val CACHES = java.util.concurrent.ConcurrentHashMap<String, StateCache>()
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

