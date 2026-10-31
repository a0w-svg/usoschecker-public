package pl.vistula.checker.ui.compose

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.compose.ui.platform.ComposeView
import pl.vistula.checker.AndroidKeyValueStore
import pl.vistula.checker.ChangeNotifier
import pl.vistula.checker.SharedRepository
import pl.vistula.checker.SyncScheduler
import pl.vistula.checker.Vault

object AndroidComposeBridge {
    @JvmStatic
    fun createComposeView(
        context: Context,
        notifier: ChangeNotifier? = null,
        syncScheduler: SyncScheduler? = null
    ): View {
        val repository = SharedRepository(Vault(context.applicationContext))
        val preferences = AndroidKeyValueStore(
            context.applicationContext.getSharedPreferences("vistula_ui_prefs", Context.MODE_PRIVATE)
        )
        return ComposeView(context).apply {
            setContent {
                VistulaApp(
                    repository = repository,
                    preferences = preferences,
                    notifier = notifier,
                    syncScheduler = syncScheduler,
                    onOpenUrl = { url ->
                        try {
                            require(pl.vistula.checker.core.WebLinks.isWebUrl(url))
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    },
                    onExportText = { filename, mimeType, content ->
                        try {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_TEXT, content)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, filename).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            true
                        } catch (_: Exception) {
                            false
                        }
                    }
                )
            }
        }
    }

    /**
     * Hosts one shared Compose tools section (for example `"staffDirectory"`) inside a View screen.
     *
     * @param fixedState demo state to show instead of the stored account, or null to use the real account
     * @param themeMode "light", "dark" or "system"
     */
    @JvmStatic
    fun createSectionView(
        context: Context,
        section: String,
        language: String,
        themeMode: String,
        fixedState: org.json.JSONObject?
    ): View {
        val repository = SharedRepository(Vault(context.applicationContext))
        val preferences = AndroidKeyValueStore(
            context.applicationContext.getSharedPreferences("vistula_ui_prefs", Context.MODE_PRIVATE)
        )
        return ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SectionHost(
                    section = section,
                    repository = repository,
                    preferences = preferences,
                    language = language,
                    themeMode = themeMode,
                    fixedState = fixedState,
                    onOpenUrl = { url ->
                        try {
                            require(pl.vistula.checker.core.WebLinks.isWebUrl(url))
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) {
                            android.util.Log.d("AndroidComposeBridge", "Ignored " + e.javaClass.simpleName)
                        }
                    }
                )
            }
        }
    }
}
