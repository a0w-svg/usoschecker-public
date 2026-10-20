package pl.vistula.checker.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.awt.FileDialog
import java.io.File
import javax.swing.JOptionPane
import pl.vistula.checker.DesktopInstanceLock
import pl.vistula.checker.DesktopKeyValueStore
import pl.vistula.checker.DesktopNotifier
import pl.vistula.checker.DesktopPlatform
import pl.vistula.checker.DesktopSyncScheduler
import pl.vistula.checker.DesktopVault
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.core.ApiLanguage
import pl.vistula.checker.ui.compose.VistulaApp
import pl.vistula.checker.ui.compose.VistulaTab

private class DesktopSession {
    private val directory = DesktopPlatform.defaultDataDir()
    private val instance = DesktopInstanceLock(directory)
    init { Runtime.getRuntime().addShutdownHook(Thread({ instance.close() }, "desktop-instance-lock")) }
    val preferences = DesktopKeyValueStore(File(directory, "preferences.json"))
    val notifier = DesktopNotifier()
    val repository = SharedRepository(DesktopVault(directory),
        { language() }, { preferences.putString("attendance_data", "{}") }, notifier)
    val scheduler = DesktopSyncScheduler(repository)
    fun language() = ApiLanguage.normalize(preferences.getString("ui_language", preferences.getString("language", "pl")))
}

fun main() = application {
    val startup = remember { runCatching { DesktopSession() } }
    val session = startup.getOrNull()
    DisposableEffect(session) {
        session?.scheduler?.schedulePeriodicSync(true)
        onDispose { session?.scheduler?.shutdown(); session?.notifier?.close() }
    }
    var shortcutTab by remember { mutableStateOf<VistulaTab?>(null) }
    var navigationTrigger by remember { mutableIntStateOf(0) }
    var syncTrigger by remember { mutableIntStateOf(0) }
    var demoTrigger by remember { mutableIntStateOf(0) }
    Window(onCloseRequest = ::exitApplication, title = "Vistula Checker — USOS Desktop",
        state = rememberWindowState(width = 1120.dp, height = 780.dp),
        onPreviewKeyEvent = { event ->
            val modifier = event.isCtrlPressed || event.isMetaPressed
            if (event.type != KeyEventType.KeyDown) false else when {
                event.key == Key.F5 || modifier && event.key == Key.R -> { syncTrigger++; true }
                modifier && event.key == Key.D -> { demoTrigger++; true }
                modifier && event.key in listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five) -> {
                    shortcutTab = when (event.key) {
                        Key.One -> VistulaTab.HOME; Key.Two -> VistulaTab.SCHEDULE; Key.Three -> VistulaTab.GRADES
                        Key.Four -> VistulaTab.HISTORY; else -> VistulaTab.MORE
                    }
                    navigationTrigger++; true
                }
                else -> false
            }
        }) {
        if (session == null) {
            androidx.compose.material3.MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text(if (startup.exceptionOrNull() is DesktopInstanceLock.AlreadyRunning)
                        "Aplikacja jest już uruchomiona. / The application is already running."
                        else "Nie można otworzyć prywatnego magazynu aplikacji. / Cannot open private application storage.")
                    androidx.compose.material3.Button(onClick = ::exitApplication) { androidx.compose.material3.Text("Zamknij / Close") }
                }
            }
        } else {
            fun label(pl: String, en: String, uk: String) = when (session.language()) { "en" -> en; "uk" -> uk; else -> pl }
            VistulaApp(repository = session.repository, preferences = session.preferences,
                notifier = session.notifier, syncScheduler = session.scheduler,
                externalTabTrigger = shortcutTab, externalNavigationTrigger = navigationTrigger,
                externalSyncTrigger = syncTrigger, externalDemoTrigger = demoTrigger,
                onOpenUrl = { address ->
                    try {
                        val uri = DesktopPlatform.browserUri(address)
                        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                        Desktop.getDesktop().browse(uri)
                    } catch (_: Exception) {
                        JOptionPane.showMessageDialog(window,
                            label("Nie można otworzyć przeglądarki dla tego adresu.", "Could not open a browser for this address.", "Не вдалося відкрити браузер для цієї адреси."),
                            "Vistula Checker", JOptionPane.ERROR_MESSAGE)
                    }
                },
                onExportText = export@{ filename, _, content ->
                    val dialog = FileDialog(window, label("Zapisz plik", "Save file", "Зберегти файл"), FileDialog.SAVE)
                    try {
                        dialog.file = File(filename).name
                        dialog.isVisible = true
                        val directory = dialog.directory ?: return@export false
                        val selected = dialog.file ?: return@export false
                        val target = File(directory, selected)
                        if (target.exists() && JOptionPane.showConfirmDialog(window,
                            label("Zastąpić istniejący plik?", "Replace the existing file?", "Замінити наявний файл?"),
                            label("Zapisz plik", "Save file", "Зберегти файл"), JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return@export false
                        DesktopPlatform.writeExport(target, content)
                        true
                    } catch (_: Exception) {
                        JOptionPane.showMessageDialog(window,
                            label("Nie udało się zapisać pliku. Sprawdź uprawnienia i wolne miejsce.", "Could not save the file. Check permissions and available space.", "Не вдалося зберегти файл. Перевірте дозволи та вільне місце."),
                            "Vistula Checker", JOptionPane.ERROR_MESSAGE)
                        false
                    } finally { dialog.dispose() }
                })
        }
    }
}
