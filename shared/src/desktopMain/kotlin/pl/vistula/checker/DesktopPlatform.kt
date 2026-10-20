package pl.vistula.checker

import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermissions
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
        val path = file.toPath()
        if (Files.isSymbolicLink(path)) throw IOException("Symbolic links are not allowed for private storage")
        if (path.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(if (file.isDirectory) "rwx------" else "rw-------"))
        } else {
            val acl = Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                ?: throw IOException("Private storage permissions are unsupported")
            val owner = acl.owner
            val entry = java.nio.file.attribute.AclEntry.newBuilder()
                .setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission::class.java)).build()
            acl.acl = listOf(entry)
        }
    }

    internal fun readPrivate(file: File, maxBytes: Int): ByteArray {
        restrictPermissions(file)
        if (Files.size(file.toPath()) > maxBytes) throw IOException("Private storage exceeds its size limit")
        return Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { stream ->
            val bytes = stream.readNBytes(maxBytes + 1)
            if (bytes.size > maxBytes) throw IOException("Private storage exceeds its size limit")
            bytes
        }
    }

    fun browserUri(address: String): java.net.URI {
        require(pl.vistula.checker.core.WebLinks.isWebUrl(address)) { "Only ordinary web links can be opened" }
        return java.net.URI(address)
    }

    fun writeExport(target: File, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 32 * 1024 * 1024) { "Export is too large" }
        atomicWrite(target, bytes)
    }

    internal fun atomicWrite(target: File, bytes: ByteArray) {
        val parent = target.absoluteFile.parentFile
        Files.createDirectories(parent.toPath())
        val posix = parent.toPath().fileSystem.supportedFileAttributeViews().contains("posix")
        // Create a unique file with private permissions before any key material is written.
        val attrs = if (posix) arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) else emptyArray()
        val temp = Files.createTempFile(parent.toPath(), ".vistula-", ".tmp", *attrs)
        try {
            restrictPermissions(temp.toFile())
            Files.write(temp, bytes)
            try {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            restrictPermissions(target)
        } finally {
            Files.deleteIfExists(temp)
        }
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
    override val storePath: String = directory.toPath().toAbsolutePath().normalize().toString()
    private val storageLock = locks.computeIfAbsent(storePath) { Any() }

    init {
        Files.createDirectories(directory.toPath())
        DesktopPlatform.restrictPermissions(directory)
    }

    private fun <T> locked(operation: () -> T): T = synchronized(storageLock) {
        val lockFile = File(directory, ".vault.lock")
        java.nio.channels.FileChannel.open(lockFile.toPath(), java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS).use { channel ->
            DesktopPlatform.restrictPermissions(lockFile)
            channel.lock().use { operation() }
        }
    }

    private fun key(): SecretKey {
        if (Files.exists(keyFile.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            val raw = DesktopPlatform.readPrivate(keyFile, 32)
            if (raw.size != 32) throw IOException("Invalid vault key; existing data was preserved")
            return SecretKeySpec(raw, "AES")
        }
        if (Files.exists(stateFile.toPath(), LinkOption.NOFOLLOW_LINKS)) throw IOException("Missing vault key; existing data was preserved")
        val raw = ByteArray(32)
        SecureRandom().nextBytes(raw)
        DesktopPlatform.atomicWrite(keyFile, raw)
        return SecretKeySpec(raw, "AES")
    }

    override fun read(): JSONObject = locked {
        if (!Files.exists(stateFile.toPath(), LinkOption.NOFOLLOW_LINKS)) return@locked JSONObject()
        val bytes = DesktopPlatform.readPrivate(stateFile, MAX_STATE_BYTES)
        if (bytes.size < 29 || bytes[0].toInt() != 1) throw IOException("Cannot read saved data")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 1, 12))
        JSONObject(String(cipher.doFinal(bytes, 13, bytes.size - 13), StandardCharsets.UTF_8))
    }

    override fun write(state: JSONObject): Unit = locked {
        val plain = state.toString().toByteArray(StandardCharsets.UTF_8)
        if (plain.size > MAX_STATE_BYTES - 29) throw IOException("Private storage exceeds its size limit")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        val out = ByteArray(1 + iv.size + encrypted.size)
        out[0] = 1
        System.arraycopy(iv, 0, out, 1, iv.size)
        System.arraycopy(encrypted, 0, out, 1 + iv.size, encrypted.size)
        DesktopPlatform.atomicWrite(stateFile, out)
    }

    override fun clear(): Unit = locked {
        Files.deleteIfExists(stateFile.toPath())
        Files.deleteIfExists(keyFile.toPath())
    }

    companion object {
        private const val MAX_STATE_BYTES = 32 * 1024 * 1024
        private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
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
            JSONObject(String(DesktopPlatform.readPrivate(file, 1024 * 1024), StandardCharsets.UTF_8))
        } catch (e: Exception) {
            JSONObject()
        }
    }

    @Synchronized
    private fun save(json: JSONObject) {
        DesktopPlatform.atomicWrite(file, json.toString(2).toByteArray(StandardCharsets.UTF_8))
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
class DesktopNotifier : ChangeNotifier, AutoCloseable {
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

    override fun close() {
        val icon = synchronized(this) { trayIcon.also { trayIcon = null } } ?: return
        java.awt.EventQueue.invokeLater { if (SystemTray.isSupported()) SystemTray.getSystemTray().remove(icon) }
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
        val account = repository.read()
        if (account.optString("token").isEmpty() || account.optString("token") == "demo") return
        val now = Instant.now()
        val next = ReminderPlanner.next(snapshot, emptySet(), now) ?: return
        val delayMs = (ReminderPlanner.triggerAt(next, leadMinutes, now) - now.toEpochMilli()).coerceAtLeast(1000L)
        reminderFuture = executor.schedule(
            {
                if (SharedRepository.sameAccount(account, repository.read())) {
                    onReminderTriggered(next.record.title, next.record.value)
                }
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

