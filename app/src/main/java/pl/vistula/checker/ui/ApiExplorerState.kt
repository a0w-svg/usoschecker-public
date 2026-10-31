package pl.vistula.checker.ui

import android.os.Bundle
import org.json.JSONException
import org.json.JSONObject
import pl.vistula.checker.core.ApiCatalog

/** Catalog filters, open method and request drafts of the API explorer; survives recreation through [save] / [restore]. */
class ApiExplorerState {
    var module: String = ""
    var query: String = ""
    var limit: Int = DEFAULT_LIMIT
    var favoritesOnly: Boolean = false
    var favorites: MutableSet<String> = HashSet()
    var drafts: JSONObject = JSONObject()
    var currentMethod: String? = null
    var currentMethodJson: JSONObject? = null

    /** Method that the picked upload file belongs to. */
    var uploadMethod: String? = null

    /** Returns to the catalog list from a method's detail page. */
    fun closeMethod() {
        currentMethod = null
        currentMethodJson = null
    }

    /** Shows [selectedModule] with a fresh search and page size. */
    fun openModule(selectedModule: String) {
        module = selectedModule
        query = ""
        limit = DEFAULT_LIMIT
        favoritesOnly = false
    }

    fun save(out: Bundle) {
        out.putString("apiModule", module)
        out.putString("currentMethod", currentMethod)
        out.putString("uploadMethod", uploadMethod)
        try {
            out.putString("apiDrafts", ApiCatalog.restorableDrafts(drafts).toString())
        } catch (ignored: JSONException) {
        }
        out.putString("apiQuery", query)
        out.putInt("apiLimit", limit)
        out.putBoolean("apiFavoritesOnly", favoritesOnly)
    }

    fun restore(bundle: Bundle) {
        module = bundle.getString("apiModule", "")
        try {
            drafts = JSONObject(bundle.getString("apiDrafts", "{}"))
        } catch (ignored: JSONException) {
        }
        currentMethod = bundle.getString("currentMethod")
        uploadMethod = bundle.getString("uploadMethod")
        query = bundle.getString("apiQuery", "")
        limit = bundle.getInt("apiLimit", DEFAULT_LIMIT)
        favoritesOnly = bundle.getBoolean("apiFavoritesOnly")
    }

    companion object {
        const val DEFAULT_LIMIT = 60
    }
}
