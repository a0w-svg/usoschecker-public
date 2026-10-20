package pl.vistula.checker

import java.nio.file.Files
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.json.JSONObject
import pl.vistula.checker.core.PaymentInfo
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.ui.compose.ToolReadModels

class DesktopRegressionTest {
    @Test fun aSecondDesktopInstanceCannotOpenTheSameAccountDirectory() {
        val directory = Files.createTempDirectory("desktop-instance-regression").toFile()
        try {
            DesktopInstanceLock(directory).use {
                assertFailsWith<DesktopInstanceLock.AlreadyRunning> { DesktopInstanceLock(directory) }
            }
            DesktopInstanceLock(directory).use { assertTrue(directory.resolve(".instance.lock").exists()) }
        } finally { directory.deleteRecursively() }
    }

    @Test fun legacyDemoRecoveryCannotClearAnAuthenticatedAccount() {
        val directory = Files.createTempDirectory("legacy-demo-recovery").toFile()
        try {
            val vault = DesktopVault(directory)
            val repository = SharedRepository(vault)
            vault.write(JSONObject().put("token", "fixture-personal-token"))
            assertFalse(repository.discardLegacyDemo())
            assertEquals("fixture-personal-token", vault.read().getString("token"))
            vault.write(JSONObject().put("token", "demo"))
            assertTrue(repository.discardLegacyDemo())
            assertEquals(0, vault.read().length())
        } finally { directory.deleteRecursively() }
    }

    @Test fun demoCannotOverwriteThePersonalVaultOrAttendancePreferences() {
        val directory = Files.createTempDirectory("desktop-demo-regression").toFile()
        try {
            val realVault = DesktopVault(directory)
            realVault.write(JSONObject().put("token", "fixture-personal-token").put("tasks", "personal-task"))
            val preferences = DesktopKeyValueStore(directory.resolve("prefs.json"))
            preferences.putString("attendance_data", "personal-attendance")
            val encrypted = directory.resolve("state.bin").readBytes()
            val demo = DemoWorkspace()
            demo.repository.update { it.put("tasks", "demo-task") }
            demo.preferences.putString("attendance_data", "demo-attendance")
            demo.reset(true)
            assertTrue(encrypted.contentEquals(directory.resolve("state.bin").readBytes()))
            assertEquals("fixture-personal-token", realVault.read().getString("token"))
            assertEquals("personal-attendance", preferences.getString("attendance_data"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun demoRefreshNeverCallsAnAuthenticatedApiOrSnapshotSource() {
        val demo = DemoWorkspace()
        assertEquals("demo", demo.repository.read().optString("token"))
        assertTrue(demo.repository.sync(true, { error("Demo must remain offline") }).isEmpty())
        assertFailsWith<IllegalStateException> {
            StudentServiceLoader.refresh(demo.repository, "news", SharedRepository.ClientFactory { throw AssertionError("No live client for demo") })
        }
    }

    @Test fun concurrentForegroundRequestsAcquireOnlyOneOperation() {
        val gate = OperationGate()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val start = CountDownLatch(1)
            val tasks = (1..24).map { pool.submit(Callable { start.await(); gate.tryStart() }) }
            start.countDown()
            assertEquals(1, tasks.count { it.get(5, TimeUnit.SECONDS) })
            gate.finish()
            assertTrue(gate.tryStart())
            gate.finish()
        } finally { pool.shutdownNow() }
    }

    @Test fun twoVaultInstancesShareOneKeyDuringConcurrentWrites() {
        val directory = Files.createTempDirectory("desktop-vault-concurrent").toFile()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val vaults = listOf(DesktopVault(directory), DesktopVault(directory.resolve("../${directory.name}")))
            val start = CountDownLatch(1)
            val tasks = vaults.mapIndexed { writer, vault -> pool.submit(Callable {
                start.await()
                repeat(20) {
                    vault.write(JSONObject().put("writer", writer).put("iteration", it))
                    assertTrue(vault.read().getInt("writer") in 0..1)
                }
            }) }
            start.countDown()
            tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            assertTrue(vaults[0].read().getInt("writer") in 0..1)
        } finally { pool.shutdownNow(); directory.deleteRecursively() }
    }

    @Test fun logoutDeletesTheEncryptionKeyAndAllowsANewAccount() {
        val directory = Files.createTempDirectory("desktop-logout-key").toFile()
        try {
            val vault = DesktopVault(directory)
            vault.write(JSONObject().put("token", "old-fixture"))
            vault.clear()
            assertFalse(directory.resolve("state.bin").exists())
            assertFalse(directory.resolve("vault.key").exists())
            vault.write(JSONObject().put("token", "new-fixture"))
            assertEquals("new-fixture", vault.read().getString("token"))
        } finally { directory.deleteRecursively() }
    }

    @Test fun symlinkedStateIsNotReadAndExportDoesNotFollowDestinationSymlinks() {
        val directory = Files.createTempDirectory("desktop-symlink-regression").toFile()
        try {
            if (!directory.toPath().fileSystem.supportedFileAttributeViews().contains("posix")) return
            val vault = DesktopVault(directory)
            vault.write(JSONObject().put("token", "fixture"))
            val victim = directory.resolve("victim.txt")
            victim.writeText("keep")
            Files.delete(directory.resolve("state.bin").toPath())
            Files.createSymbolicLink(directory.resolve("state.bin").toPath(), victim.toPath())
            assertFailsWith<java.io.IOException> { vault.read() }
            val export = directory.resolve("export.ics")
            Files.createSymbolicLink(export.toPath(), victim.toPath())
            DesktopPlatform.writeExport(export, "calendar")
            assertEquals("keep", victim.readText())
            assertEquals("calendar", export.readText())
        } finally { directory.deleteRecursively() }
    }

    @Test fun desktopBrowserCannotLaunchLocalFilesOrExecutableSchemes() {
        for (url in listOf("file:///etc/passwd", "javascript:alert(1)", "data:text/html,payload", "mailto:target@example.org", "https://user@host.test/", "https://example.org/\nrun")) {
            assertFailsWith<IllegalArgumentException>(url) { DesktopPlatform.browserUri(url) }
        }
        assertEquals("https", DesktopPlatform.browserUri("https://usosweb.vistula.edu.pl/").scheme)
    }

    @Test fun paymentRefreshUsesNewerServiceCacheAndKeepsFreshFullSyncData() {
        fun payments(amount: Double) = PaymentInfo(listOf(PaymentInfo.Account("1", "Account", "PLN", "", amount)), emptyList())
        val snapshot = Snapshot("2026-10-20", "2026-10-27").apply { this.payments = payments(10.0) }
        val state = JSONObject().put("successAt", 100L).put("studentServices", JSONObject().put("payments", JSONObject()
            .put("fetchedAt", 200L).put("data", JSONObject().put("en", payments(20.0).json()))))
        assertEquals(20.0, ToolReadModels.payments(state, snapshot, "en")!!.accounts[0].balance)
        state.put("successAt", 300L)
        assertEquals(10.0, ToolReadModels.payments(state, snapshot, "en")!!.accounts[0].balance)
    }
}
