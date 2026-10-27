package pl.vistula.checker

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class DesktopVaultKeyProtectionTest {
    /** Reversible stand-in for an OS secret store: bound to a "device" byte. */
    private class FakeProtector(private val device: Byte) : KeyProtector {
        override fun protect(raw: ByteArray) = raw.map { (it.toInt() xor device.toInt()).toByte() }.toByteArray() + byteArrayOf(device)
        override fun unprotect(blob: ByteArray): ByteArray {
            if (blob.isEmpty() || blob.last() != device) throw IOException("wrong account")
            return blob.copyOf(blob.size - 1).map { (it.toInt() xor device.toInt()).toByte() }.toByteArray()
        }
    }

    @Test
    fun wrappedKeyIsNotStoredInPlaintextAndRoundTrips() {
        val dir = Files.createTempDirectory("vistula-wrapped-key").toFile()
        try {
            DesktopVault(dir, FakeProtector(0x5A)).write(JSONObject().put("token", "fixture"))
            val stored = Files.readAllBytes(dir.resolve("vault.key").toPath())
            assertTrue(stored.size > 32)
            assertEquals("VKW1", String(stored, 0, 4, Charsets.US_ASCII))
            assertEquals("fixture", DesktopVault(dir, FakeProtector(0x5A)).read().getString("token"))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun legacyPlaintextKeyIsMigratedWithoutLosingData() {
        val dir = Files.createTempDirectory("vistula-key-migration").toFile()
        try {
            DesktopVault(dir, null).write(JSONObject().put("token", "fixture"))
            val legacy = Files.readAllBytes(dir.resolve("vault.key").toPath())
            assertEquals(32, legacy.size)
            assertEquals("fixture", DesktopVault(dir, FakeProtector(0x33)).read().getString("token"))
            val migrated = Files.readAllBytes(dir.resolve("vault.key").toPath())
            assertTrue(migrated.size > 32)
            assertFalse(migrated.toList().windowed(32).any { it.toByteArray().contentEquals(legacy) })
            assertEquals("fixture", DesktopVault(dir, FakeProtector(0x33)).read().getString("token"))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun otherAccountOrMissingProtectorCannotUseOrReplaceWrappedKey() {
        val dir = Files.createTempDirectory("vistula-key-foreign").toFile()
        try {
            DesktopVault(dir, FakeProtector(0x11)).write(JSONObject().put("token", "fixture"))
            val before = Files.readAllBytes(dir.resolve("vault.key").toPath())
            assertFailsWith<IOException> { DesktopVault(dir, FakeProtector(0x22)).read() }
            assertFailsWith<IOException> { DesktopVault(dir, null).write(JSONObject()) }
            assertContentEquals(before, Files.readAllBytes(dir.resolve("vault.key").toPath()))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun windowsAclRestrictsVaultFilesToOwnerOnly() {
        val dir = Files.createTempDirectory("vistula-acl-test").toFile()
        try {
            DesktopVault(dir, FakeProtector(0x44)).write(JSONObject().put("token", "fixture"))
            val keyPath = dir.resolve("vault.key").toPath()
            if (keyPath.fileSystem.supportedFileAttributeViews().contains("posix")) return
            val aclView = Files.getFileAttributeView(keyPath, AclFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                ?: return
            val entries = aclView.acl
            assertEquals(1, entries.size)
            assertEquals(aclView.owner, entries[0].principal())
            assertEquals(AclEntryType.ALLOW, entries[0].type())
        } finally { dir.deleteRecursively() }
    }
}
