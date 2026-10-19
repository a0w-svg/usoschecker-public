package pl.vistula.checker.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import java.awt.Frame
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import pl.vistula.checker.DesktopKeyValueStore
import pl.vistula.checker.DesktopNotifier
import pl.vistula.checker.DesktopSyncScheduler
import pl.vistula.checker.DesktopVault
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.UsosClient
import pl.vistula.checker.ui.compose.VistulaApp
import pl.vistula.checker.ui.compose.VistulaTab

fun main() = application {
    val vault = remember { DesktopVault() }
    val notifier = remember { DesktopNotifier() }
    val repository = remember { SharedRepository(vault = vault, changeNotifier = notifier) }
    val preferences = remember { DesktopKeyValueStore() }
    val scheduler = remember { DesktopSyncScheduler(repository) }

    DisposableEffect(Unit) {
        scheduler.schedulePeriodicSync(true)
        onDispose {
            scheduler.shutdown()
        }
    }

    var shortcutTab by remember { mutableStateOf<VistulaTab?>(null) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Vistula Checker — USOS Desktop",
        state = rememberWindowState(width = 1120.dp, height = 780.dp),
        onPreviewKeyEvent = { event ->
            val modPressed = event.isCtrlPressed || event.isMetaPressed
            if (modPressed && event.type == KeyEventType.KeyDown) {
                when (event.key) {
                    Key.One -> { shortcutTab = VistulaTab.HOME; true }
                    Key.Two -> { shortcutTab = VistulaTab.SCHEDULE; true }
                    Key.Three -> { shortcutTab = VistulaTab.GRADES; true }
                    Key.Four -> { shortcutTab = VistulaTab.HISTORY; true }
                    Key.Five -> { shortcutTab = VistulaTab.MORE; true }
                    else -> false
                }
            } else {
                false
            }
        }
    ) {
        VistulaApp(
            repository = repository,
            preferences = preferences,
            notifier = notifier,
            syncScheduler = scheduler,
            externalTabTrigger = shortcutTab,
            onOpenUrl = { url ->
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(url))
                    }
                } catch (_: Exception) {
                }
            },
            onExportText = { filename, _, content ->
                try {
                    val dialog = FileDialog(null as Frame?, "Zapisz plik", FileDialog.SAVE)
                    dialog.file = filename
                    dialog.isVisible = true
                    val dir = dialog.directory
                    val file = dialog.file
                    if (dir != null && file != null) {
                        File(dir, file).writeText(content, StandardCharsets.UTF_8)
                    }
                } catch (_: Exception) {
                }
            }
        )
    }
}

