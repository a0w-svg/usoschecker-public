package pl.vistula.checker

import android.content.Context
import java.util.function.Consumer
import java.util.function.Supplier
import org.json.JSONObject
import pl.vistula.checker.core.Change
import pl.vistula.checker.core.Snapshot
import pl.vistula.checker.ui.Language

/** Android adapter around KMP SharedRepository using Android Keystore Vault and SharedPreferences. */
class Repository(c: Context, clients: SharedRepository.ClientFactory) : StateRepository {
    private val delegate: SharedRepository

    class EditSession internal constructor(internal val inner: SharedRepository.EditSession)

    fun interface ClientFactory : SharedRepository.ClientFactory {
        override fun create(state: JSONObject): UsosClient
    }

    fun interface Update : StateRepository.Update {
        @Throws(Exception::class)
        override fun apply(state: JSONObject)
    }

    fun interface SnapshotSource : SharedRepository.SnapshotSource {
        @Throws(Exception::class)
        override fun fetch(state: JSONObject): Snapshot
    }

    constructor(c: Context) : this(c, SharedRepository.ClientFactory { UsosClient(it) })

    init {
        val appContext = c.applicationContext
        val attendance = c.getSharedPreferences("attendance_tracker", Context.MODE_PRIVATE)
        delegate = SharedRepository(
            Vault(c),
            Supplier { Language.currentLocale(appContext).language },
            Runnable { attendance.edit().clear().apply() },
            ChangeNotifier { changes -> NotificationHelper.notifyChanges(appContext, changes) },
            clients
        )
    }

    fun editSession(displayedState: JSONObject): EditSession =
        EditSession(delegate.editSession(displayedState))

    @Throws(Exception::class)
    fun update(session: EditSession, operation: StateRepository.Update) {
        delegate.update(session.inner, operation)
    }

    @Throws(Exception::class)
    override fun read(): JSONObject = delegate.read()

    @Throws(Exception::class)
    override fun update(operation: StateRepository.Update) {
        delegate.update(operation)
    }

    @Throws(Exception::class)
    fun begin(key: String, secret: String, scopes: String?) {
        delegate.begin(key, secret, scopes)
    }

    @Throws(Exception::class)
    fun finish(pin: String?) {
        delegate.finish(pin)
    }

    @Throws(Exception::class)
    fun sync(manual: Boolean): List<Change> = delegate.sync(manual)

    @Throws(Exception::class)
    fun syncWithProgress(manual: Boolean, progress: Consumer<String>): List<Change> =
        delegate.syncWithProgress(manual, progress)

    @Throws(Exception::class)
    fun syncAndNotify(manual: Boolean, progress: Consumer<String>): List<Change> =
        delegate.syncAndNotify(manual, progress)

    @Throws(Exception::class)
    fun sync(manual: Boolean, source: SharedRepository.SnapshotSource): List<Change> =
        delegate.sync(manual, source)

    @Throws(Exception::class)
    fun sync(
        manual: Boolean,
        source: SharedRepository.SnapshotSource,
        publish: Consumer<List<Change>>
    ): List<Change> = delegate.sync(manual, source, publish, false)

    fun disconnect() {
        delegate.disconnect()
    }

    companion object {
        @JvmField val LOCK: Any = SharedRepository.LOCK

        @JvmStatic
        @Throws(Exception::class)
        fun localizedView(source: JSONObject, language: String?): JSONObject =
            SharedRepository.localizedView(source, language)

        @JvmStatic
        fun sameAccount(expected: JSONObject, current: JSONObject): Boolean =
            SharedRepository.sameAccount(expected, current)

        @JvmStatic
        fun cause(e: Exception): Exception = SharedRepository.cause(e)

        @JvmStatic
        fun message(e: Exception): String = SharedRepository.message(e)
    }
}

