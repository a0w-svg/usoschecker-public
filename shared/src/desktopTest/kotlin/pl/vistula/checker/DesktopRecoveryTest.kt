package pl.vistula.checker

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.json.JSONObject
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Record
import pl.vistula.checker.core.SyncProgress

class DesktopRecoveryTest {
    @Test
    fun damagedStateIsReportedAsUnreadableAndClearingRecovers() {
        val dir = Files.createTempDirectory("vistula-unreadable").toFile()
        try {
            val vault = DesktopVault(dir, null)
            vault.write(JSONObject().put("token", "fixture"))
            val file = dir.resolve("state.bin")
            val bytes = file.readBytes()
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
            file.writeBytes(bytes)
            assertFailsWith<VaultUnreadableException> { DesktopVault(dir, null).read() }

            file.writeBytes(byteArrayOf(1, 2, 3))
            assertFailsWith<VaultUnreadableException> { DesktopVault(dir, null).read() }

            // The same recovery the app offers: clearing, after which the store is empty and writable again.
            SharedRepository(DesktopVault(dir, null)).disconnect()
            val fresh = DesktopVault(dir, null)
            assertEquals(0, fresh.read().length())
            fresh.write(JSONObject().put("token", "new"))
            assertEquals("new", DesktopVault(dir, null).read().getString("token"))
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun unreadableStoreHasItsOwnMessageInEveryLanguage() {
        val failure = VaultUnreadableException()
        assertEquals("storage", SyncProgress.errorKind(failure))
        val messages = listOf("pl", "en", "uk").map { SyncProgress.error(failure, it) }
        assertEquals(3, messages.toSet().size)
        val network = SyncProgress.error(java.io.IOException("x"), "en")
        assertTrue(messages[1] != network, "An unreadable store must not be described as a network failure")
        // The repository's own text must not call it a download failure either.
        assertTrue(SharedRepository.message(failure).contains("magazynu"))
    }

    @Test
    fun changeNotificationTitleFollowsTheLanguage() {
        fun change(id: String, title: String) =
            Change(null, Record("grade:$id", "ocena", title, "5", "2026-11-10 10:00:00", ""), 1L)
        val two = listOf(change("1", "Finanse"), change("2", "Prawo"))
        assertEquals("Wykryto zmiany w USOS (2)", DesktopNotifier.message(two, "pl").first)
        assertEquals("Changes found in USOS (2)", DesktopNotifier.message(two, "en").first)
        assertEquals("Виявлено зміни в USOS (2)", DesktopNotifier.message(two, "uk").first)
        assertEquals("Wykryto zmiany w USOS (2)", DesktopNotifier.message(two, "de").first)
        val (title, body) = DesktopNotifier.message(two.take(1), "en")
        assertTrue(title.endsWith(": Finanse"))
        assertEquals("Finanse: 5", body)
    }
}
