package pl.vistula.checker

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONObject
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.ReminderPlanner
import pl.vistula.checker.core.Snapshot

/** Desktop storage and platform services for Windows, macOS, and Linux. */
object DesktopPlatform {
    @JvmStatic
    fun defaultDataDir(): File {
        val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        val home = System.getProperty("user.home", ".")
        val dir = when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA")
                if (!localAppData.isNullOrBlank()) File(localAppData, "VistulaChecker")
                else File(home, "AppData/Local/VistulaChecker")
            }
            os.contains("mac") -> File(home, "Library/Application Support/VistulaChecker")
            else -> {
                val xdg = System.getenv("XDG_DATA_HOME")
                if (!xdg.isNullOrBlank()) File(xdg, "vistula-checker")
                else File(home, ".local/share/vistula-checker")
            }
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    internal fun restrictPermissions(file: File) {
        try {
            val path = file.toPath()
            if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(
                    path,
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                )
            } else {
                file.setReadable(false, false)
                file.setReadable(true, true)
                file.setWritable(false, false)
                file.setWritable(true, true)
            }
        } catch (ignored: Exception) {}
    }

    internal fun atomicWrite(target: File, bytes: ByteArray) {
        val parent = target.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val temp = File(parent, target.name + ".tmp")
        Files.write(temp.toPath(), bytes)
        restrictPermissions(temp)
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: IOException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        restrictPermissions(target)
    }
}

/**
 * AES-256-GCM encrypted state store for Desktop with the same framing (`[1, iv(12), ciphertext]`)
 * as Android's Keystore Vault.
 */
class DesktopVault @JvmOverloads constructor(
    private val directory: File = DesktopPlatform.defaultDataDir()
) : VaultStore {
    private val stateFile = File(directory, "state.bin")
    private val keyFile = File(directory, "vault.key")
    override val storePath: String = directory.absolutePath

    @Synchronized
    @Throws(Exception::class)
    private fun key(): SecretKey {
        if (keyFile.exists()) {
            val raw = Files.readAllBytes(keyFile.toPath())
            if (raw.size == 32) return SecretKeySpec(raw, "AES")
        }
        val raw = ByteArray(32)
        SecureRandom().nextBytes(raw)
        DesktopPlatform.atomicWrite(keyFile, raw)
        return SecretKeySpec(raw, "AES")
    }

    @Synchronized
    @Throws(Exception::class)
    override fun read(): JSONObject {
        if (!stateFile.exists()) return JSONObject()
        val bytes = Files.readAllBytes(stateFile.toPath())
        if (bytes.size < 29 || bytes[0].toInt() != 1) {
            throw IOException("Nie można odczytać zapisanych danych.")
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 1, 12))
        return JSONObject(String(cipher.doFinal(bytes, 13, bytes.size - 13), StandardCharsets.UTF_8))
    }

    @Synchronized
    @Throws(Exception::class)
    override fun write(state: JSONObject) {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(state.toString().toByteArray(StandardCharsets.UTF_8))
        val out = ByteArray(1 + iv.size + encrypted.size)
        out[0] = 1
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(encrypted, 0, out, 1 + iv.size, encrypted.size)
        DesktopPlatform.atomicWrite(stateFile, out)
    }

    @Synchronized
    override fun clear() {
        if (stateFile.exists()) stateFile.delete()
    }
}

/** File-backed JSON preferences store for Desktop. */
class DesktopKeyValueStore @JvmOverloads constructor(
    private val file: File = File(DesktopPlatform.defaultDataDir(), "preferences.json")
) : KeyValueStore {
    @Synchronized
    private fun load(): JSONObject {
        if (!file.exists()) return JSONObject()
        return try {
            JSONObject(Files.readString(file.toPath(), StandardCharsets.UTF_8))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    @Synchronized
    private fun save(json: JSONObject) {
        try {
            DesktopPlatform.atomicWrite(file, json.toString(2).toByteArray(StandardCharsets.UTF_8))
        } catch (ignored: Exception) {}
    }

    @Synchronized
    override fun getString(key: String, defaultValue: String): String =
        load().optString(key, defaultValue)

    @Synchronized
    override fun putString(key: String, value: String) {
        val json = load()
        json.put(key, value)
        save(json)
    }

    @Synchronized
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        load().optBoolean(key, defaultValue)

    @Synchronized
    override fun putBoolean(key: String, value: Boolean) {
        val json = load()
        json.put(key, value)
        save(json)
    }

    @Synchronized
    override fun getLong(key: String, defaultValue: Long): Long =
        load().optLong(key, defaultValue)

    @Synchronized
    override fun putLong(key: String, value: Long) {
        val json = load()
        json.put(key, value)
        save(json)
    }

    @Synchronized
    override fun clear() {
        if (file.exists()) file.delete()
    }
}

/** System tray notification publisher for Desktop. */
class DesktopNotifier : ChangeNotifier {
    private var trayIcon: TrayIcon? = null

    @Synchronized
    private fun ensureTrayIcon(): TrayIcon? {
        if (!SystemTray.isSupported()) return null
        if (trayIcon != null) return trayIcon
        return try {
            val tray = SystemTray.getSystemTray()
            val image = Toolkit.getDefaultToolkit().createImage(ByteArray(0))
            val icon = TrayIcon(image, "Vistula Checker")
            icon.isImageAutoSize = true
            tray.add(icon)
            trayIcon = icon
            icon
        } catch (ignored: Exception) {
            null
        }
    }

    override fun notifyChanges(changes: List<Change>) {
        if (changes.isEmpty()) return
        val icon = ensureTrayIcon() ?: return
        val title = if (changes.size == 1) {
            "${changes[0].action}: ${changes[0].title}"
        } else {
            "Wykryto zmiany w USOS (${changes.size})"
        }
        val body = changes.take(3).joinToString("\n") { c ->
            "${c.title}: ${c.after.ifEmpty { c.before }}"
        }
        icon.displayMessage(title, body, TrayIcon.MessageType.INFO)
    }
}

/** In-process daemon scheduler for periodic sync and class reminders on Desktop. */
class DesktopSyncScheduler(
    private val repository: SharedRepository,
    private val onReminderTriggered: (String, String) -> Unit = { _, _ -> }
) : SyncScheduler {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "vistula-desktop-scheduler").apply { isDaemon = true }
    }
    private var syncFuture: ScheduledFuture<*>? = null
    private var reminderFuture: ScheduledFuture<*>? = null

    @Synchronized
    override fun schedulePeriodicSync(enabled: Boolean) {
        syncFuture?.cancel(false)
        syncFuture = null
        if (enabled) {
            syncFuture = executor.scheduleWithFixedDelay(
                {
                    try {
                        repository.syncAndNotify(false) {}
                    } catch (ignored: Exception) {}
                },
                30,
                30,
                TimeUnit.MINUTES
            )
        }
    }

    @Synchronized
    override fun scheduleNextReminder(snapshot: Snapshot?, enabled: Boolean, leadMinutes: Int) {
        reminderFuture?.cancel(false)
        reminderFuture = null
        if (!enabled || snapshot == null) return
        val now = Instant.now()
        val next = ReminderPlanner.next(snapshot, emptySet(), now) ?: return
        val delayMs = (ReminderPlanner.triggerAt(next, leadMinutes, now) - now.toEpochMilli()).coerceAtLeast(1000L)
        reminderFuture = executor.schedule(
            {
                onReminderTriggered(next.record.title, next.record.value)
            },
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }

    @Synchronized
    fun shutdown() {
        syncFuture?.cancel(true)
        reminderFuture?.cancel(true)
        executor.shutdownNow()
    }
}

