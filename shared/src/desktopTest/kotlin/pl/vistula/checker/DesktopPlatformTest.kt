package pl.vistula.checker

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class DesktopPlatformTest {
    @Test
    fun atomicWritesIgnorePredictableTempSymlinksAndKeepFilesPrivate() {
        val directory = Files.createTempDirectory("vistula-private-test")
        try {
            if (!directory.fileSystem.supportedFileAttributeViews().contains("posix")) return
            val victim = directory.resolve("victim")
            Files.writeString(victim, "keep")
            Files.createSymbolicLink(directory.resolve("vault.key.tmp"), victim)
            DesktopVault(directory.toFile()).write(JSONObject().put("token", "fixture"))
            assertEquals("keep", Files.readString(victim))
            assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"),
                Files.getPosixFilePermissions(directory.resolve("vault.key")))
            Files.list(directory).use { files -> assertFalse(files.anyMatch { it.fileName.toString().startsWith(".vistula-") }) }
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun invalidExistingKeyIsNeverReplaced() {
        val directory = Files.createTempDirectory("vistula-invalid-key")
        try {
            val key = directory.resolve("vault.key")
            Files.writeString(key, "invalid")
            kotlin.test.assertFailsWith<java.io.IOException> { DesktopVault(directory.toFile()).write(JSONObject()) }
            assertEquals("invalid", Files.readString(key))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test
    fun desktopVaultRoundTripsAndEncryptsState() {
        val tempDir = Files.createTempDirectory("vistula_desktop_vault_test").toFile()
        try {
            val vault = DesktopVault(tempDir)
            assertEquals(0, vault.read().length())

            val secretToken = "secret_oauth_token_987654"
            val state = JSONObject()
                .put("token", secretToken)
                .put("tokenSecret", "secret_value")
                .put("user", "Jan Kowalski")
            vault.write(state)

            val rawBytes = Files.readAllBytes(tempDir.resolve("state.bin").toPath())
            assertEquals(1, rawBytes[0].toInt())
            assertFalse(String(rawBytes, Charsets.UTF_8).contains(secretToken))

            val loaded = vault.read()
            assertEquals(secretToken, loaded.getString("token"))
            assertEquals("Jan Kowalski", loaded.getString("user"))

            vault.clear()
            assertEquals(0, vault.read().length())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun desktopKeyValueStorePersistsPreferences() {
        val tempDir = Files.createTempDirectory("vistula_desktop_prefs_test").toFile()
        try {
            val store = DesktopKeyValueStore(tempDir.resolve("prefs.json"))
            assertEquals("default", store.getString("lang", "default"))
            assertFalse(store.getBoolean("dark", false))
            assertEquals(0L, store.getLong("checkedAt", 0L))

            store.putString("lang", "en")
            store.putBoolean("dark", true)
            store.putLong("checkedAt", 123456789L)

            val reloaded = DesktopKeyValueStore(tempDir.resolve("prefs.json"))
            assertEquals("en", reloaded.getString("lang", "default"))
            assertTrue(reloaded.getBoolean("dark", false))
            assertEquals(123456789L, reloaded.getLong("checkedAt", 0L))

            reloaded.clear()
            assertEquals("default", reloaded.getString("lang", "default"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun sharedRepositorySupportsEditSessionsAndDemoState() {
        val tempDir = Files.createTempDirectory("vistula_shared_repo_test").toFile()
        try {
            val vault = DesktopVault(tempDir)
            val repo = SharedRepository(vault, { "pl" })
            val demo = Demo.state(true)
            repo.update { state ->
                for (key in demo.keys()) {
                    state.put(key, demo.get(key))
                }
            }
            val readBack = repo.read()
            assertEquals("Konto demonstracyjne", readBack.getString("user"))
            assertTrue(readBack.getJSONObject("snapshot").getJSONArray("records").length() > 0)

            val session = repo.editSession(readBack)
            repo.update(session) { state -> state.put("customNote", "ok") }
            assertEquals("ok", repo.read().getString("customNote"))

            repo.disconnect()
            assertEquals(0, repo.read().length())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

