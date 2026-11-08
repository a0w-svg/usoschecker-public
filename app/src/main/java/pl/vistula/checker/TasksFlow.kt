package pl.vistula.checker

import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.core.TaskTools
import pl.vistula.checker.ui.ApiErrorUi
import pl.vistula.checker.ui.TasksScreenController

/**
 * Personal tasks: adding, editing, completing, deleting with undo, for the real account and for the separate demo
 * account. Real tasks live in the account state; demo tasks live only in memory ([demoTasks]) and in saved instance state.
 */
class TasksFlow(private val host: FlowHost, private val platon: PlatonFlow) {
    /** Tasks of the demo account; never written to the vault. */
    var demoTasks: JSONArray = JSONArray()

    fun personalTasks(): JSONArray =
        if (host.isDemo) demoTasks else (host.state.optJSONArray("tasks") ?: JSONArray())

    fun edit(existing: JSONObject?, suggestedTitle: String?) {
        TasksScreenController.showTaskDialog(host.activity, existing, suggestedTitle ?: "", personalTasks().length(), callback())
    }

    /** Callbacks for the Tasks screen; edits made through it are rejected if the account switched to/from demo meanwhile. */
    fun callback(): TasksScreenController.TaskCallback {
        val session = host.repository.editSession(host.state)
        val wasDemo = host.isDemo
        return object : TasksScreenController.TaskCallback {
            override fun onSaveTask(task: JSONObject) {
                if (host.isDemo != wasDemo) return
                save(task, session)
            }

            override fun onMutateTask(id: String, remove: Boolean) {
                if (host.isDemo != wasDemo) return
                mutate(id, remove, session)
            }

            override fun onPlatonConnect() = platon.connect()

            override fun onPlatonSync() = platon.sync()

            override fun onPlatonCourses() = platon.showCourses()

            override fun onPlatonMessages() = platon.showMessages()

            override fun onPlatonDisconnect() = platon.disconnect()

            override fun onPlatonSettings() = platon.showSettings()
        }
    }

    fun save(entry: JSONObject) {
        save(entry, host.repository.editSession(host.state))
    }

    private fun save(entry: JSONObject, session: Repository.EditSession) {
        if (host.isDemo) {
            try {
                demoTasks = TaskTools.save(demoTasks, entry)
                host.render()
            } catch (e: Exception) {
                host.showError(ApiErrorUi.message(host.activity, e))
            }
        } else {
            val repository = host.repository
            host.runTask(
                {
                    repository.update(session) { s -> s.put("tasks", TaskTools.save(s.optJSONArray("tasks"), entry)) }
                    repository.read()
                },
                { result ->
                    host.state = result
                    host.render()
                },
            )
        }
    }

    fun mutate(id: String, remove: Boolean) {
        mutate(id, remove, host.repository.editSession(host.state))
    }

    private fun mutate(id: String, remove: Boolean, session: Repository.EditSession) {
        var deleted: JSONObject? = null
        if (remove) {
            val current = personalTasks()
            for (i in 0 until current.length()) {
                val row = current.optJSONObject(i)
                if (row != null && row.optString("id") == id) {
                    deleted = row
                    break
                }
            }
        }
        val undoEntry = deleted
        val wasDemo = host.isDemo

        if (host.isDemo) {
            try {
                demoTasks = changed(demoTasks, id, remove)
                host.render()
                if (undoEntry != null) offerUndo(undoEntry, wasDemo, session)
            } catch (e: JSONException) {
                host.showError(ApiErrorUi.message(host.activity, e))
            }
        } else {
            val repository = host.repository
            host.runTask(
                {
                    repository.update(session) { s -> s.put("tasks", changed(s.optJSONArray("tasks"), id, remove)) }
                    repository.read()
                },
                { result ->
                    host.state = result
                    host.render()
                    if (undoEntry != null) offerUndo(undoEntry, wasDemo, session)
                },
            )
        }
    }

    private fun offerUndo(entry: JSONObject, wasDemo: Boolean, session: Repository.EditSession) {
        Snackbar.make(host.rootView, R.string.task_deleted, 10000).setAction(R.string.undo) {
            if (host.isBusy || host.isDemo != wasDemo) return@setAction
            if (wasDemo) {
                try {
                    demoTasks = TaskTools.restore(demoTasks, entry)
                    host.render()
                } catch (e: Exception) {
                    host.showError(ApiErrorUi.message(host.activity, e))
                }
            } else {
                val repository = host.repository
                host.runTask(
                    {
                        repository.update(session) { s ->
                            s.put("tasks", TaskTools.restore(s.optJSONArray("tasks"), entry))
                        }
                        repository.read()
                    },
                    { result ->
                        host.state = result
                        host.render()
                    },
                )
            }
        }.show()
    }

    /** Copy of [source] with task [id] removed ([remove]) or its completion toggled. */
    @Throws(JSONException::class)
    private fun changed(source: JSONArray?, id: String, remove: Boolean): JSONArray {
        val result = JSONArray()
        if (source != null) {
            for (i in 0 until source.length()) {
                var entry = JSONObject(source.getJSONObject(i).toString())
                if (entry.getString("id") == id) {
                    if (remove) continue
                    entry = TaskTools.toggled(entry)
                }
                result.put(entry)
            }
        }
        return result
    }
}
