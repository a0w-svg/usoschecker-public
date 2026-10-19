package pl.vistula.checker.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.nio.charset.StandardCharsets
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.function.Consumer
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import pl.vistula.checker.MainActivity
import pl.vistula.checker.R
import pl.vistula.checker.UsosClient
import pl.vistula.checker.core.ApiCatalog
import pl.vistula.checker.core.WebLinks

/**
 * Controller for rendering the USOS API interactive catalog and tester.
 */
class ApiExplorerController private constructor() {

    interface ApiCallback {
        fun onExecuteRequest(
            metadata: JSONObject,
            values: Map<String, String>,
            method: String,
            uploadBytes: ByteArray?,
            onSuccess: Consumer<UsosClient.Response>,
            onFailure: Consumer<String>
        )

        fun onSelectUploadFile(currentMethod: String)
        fun onSaveResponse(response: UsosClient.Response)
    }

    companion object {
        @JvmStatic
        fun renderCatalog(
            activity: MainActivity,
            content: LinearLayout,
            catalog: JSONArray,
            apiQuery: String,
            apiLimit: Int,
            favoritesOnly: Boolean,
            favorites: Set<String>,
            onQueryChanged: Consumer<String>,
            onLimitChanged: Consumer<Int>,
            onFavoritesChanged: Consumer<Boolean>,
            onShowMethod: Consumer<String>
        ) {
            ToolScreenUi.header(activity, content, R.string.tool_api_title) { menu ->
                ToolScreenUi.action(menu, if (favoritesOnly) R.string.api_all_methods else R.string.api_fav_filter) {
                    onFavoritesChanged.accept(!favoritesOnly)
                }
                val quick = menu.addSubMenu(R.string.api_key_services_heading)
                val methods = arrayOf(
                    "services/users/user",
                    "services/grades/terms2",
                    "services/tt/student",
                    "services/payments/user_accounts",
                    "services/payments/user_payments",
                    "services/theses/user",
                    "services/crstests/participant2",
                    "services/terms/terms",
                    "services/cards/mcard2",
                    "services/adm_documents/user_documents",
                    "services/news/search"
                )
                val labels = intArrayOf(
                    R.string.api_quick_profile,
                    R.string.api_quick_grades,
                    R.string.api_quick_schedule,
                    R.string.api_quick_accounts,
                    R.string.api_quick_payments,
                    R.string.api_quick_theses,
                    R.string.api_quick_tests,
                    R.string.api_quick_terms,
                    R.string.cards_title,
                    R.string.documents_title,
                    R.string.tool_news_title
                )
                for (i in methods.indices) {
                    val method = methods[i]
                    ToolScreenUi.action(quick, labels[i]) { onShowMethod.accept(method) }
                }
                ToolScreenUi.action(menu, R.string.api_offline_note) {
                    MaterialAlertDialogBuilder(activity)
                        .setMessage(
                            activity.getString(R.string.api_subtitle_methods, catalog.length()) +
                                "\n\n" + activity.getString(R.string.api_offline_note)
                        )
                        .setPositiveButton(R.string.btn_close, null)
                        .show()
                }
            }
            if (favoritesOnly) {
                UiHelper.note(activity, content, activity.getString(R.string.api_fav_filter), UiHelper.MUTED)
            }
            UiHelper.note(activity, content, activity.getString(R.string.api_catalog_scope_note), UiHelper.MUTED)
            val modules = ApiCatalog.modules(catalog)
            UiHelper.note(
                activity,
                content,
                activity.getString(R.string.api_modules_count, modules.size, catalog.length()),
                UiHelper.MUTED
            )
            val moduleValues = ArrayList<String>()
            val moduleLabels = ArrayList<String>()
            moduleValues.add("")
            moduleLabels.add(activity.getString(R.string.api_modules_all))
            for ((key, value) in modules) {
                moduleValues.add(key)
                moduleLabels.add("${ApiCatalog.label(key)} · $key ($value)")
            }
            val modulePicker = Spinner(activity)
            modulePicker.contentDescription = activity.getString(R.string.api_module_choose)
            modulePicker.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, moduleLabels)
            modulePicker.setSelection(max(0, moduleValues.indexOf(activity.getApiModule())))
            content.addView(modulePicker, LinearLayout.LayoutParams(-1, UiHelper.dp(activity, 56)))
            modulePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (modulePicker.isAttachedToWindow && moduleValues[position] != activity.getApiModule()) {
                        activity.setApiModule(moduleValues[position])
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            val search = UiHelper.searchField(activity, content, activity.getString(R.string.api_search_hint), apiQuery, null, null)
            val list = LinearLayout(activity)
            list.orientation = LinearLayout.VERTICAL
            content.addView(list)

            populateMethods(activity, list, catalog, apiQuery, apiLimit, favoritesOnly, favorites, onShowMethod, onLimitChanged)

            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, st: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, st: Int, before: Int, count: Int) {
                    onQueryChanged.accept(s.toString())
                    populateMethods(activity, list, catalog, s.toString(), apiLimit, favoritesOnly, favorites, onShowMethod, onLimitChanged)
                }

                override fun afterTextChanged(e: Editable) {}
            })
        }

        private fun populateMethods(
            activity: MainActivity,
            list: LinearLayout,
            catalog: JSONArray,
            search: String,
            apiLimit: Int,
            favoritesOnly: Boolean,
            favorites: Set<String>,
            onShowMethod: Consumer<String>,
            onLimitChanged: Consumer<Int>
        ) {
            list.removeAllViews()
            var count = 0
            var shown = 0

            try {
                for (i in 0 until catalog.length()) {
                    val entry = catalog.getJSONObject(i)
                    val name = entry.getString("name")
                    val brief = entry.optString("brief_description").trim()
                    if ((!favoritesOnly || favorites.contains(name)) && ApiCatalog.matches(entry, activity.getApiModule(), search)) {
                        ++count
                        if (shown++ < apiLimit) {
                            val b = UiHelper.button(
                                activity,
                                name.substring("services/".length) + "\n" + brief,
                                { onShowMethod.accept(name) },
                                false
                            )
                            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                            b.textSize = 13.0f
                            b.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                            b.setPadding(
                                UiHelper.dp(activity, 8),
                                UiHelper.dp(activity, 12),
                                UiHelper.dp(activity, 8),
                                UiHelper.dp(activity, 12)
                            )
                            list.addView(b, LinearLayout.LayoutParams(-1, -2))
                            val divider = View(activity)
                            divider.setBackgroundColor(activity.getColor(R.color.app_outline_soft))
                            list.addView(divider, LinearLayout.LayoutParams(-1, UiHelper.dp(activity, 1)))
                        }
                    }
                }
            } catch (_: JSONException) {
            }

            UiHelper.note(
                activity,
                list,
                if (count == 0) {
                    activity.getString(R.string.api_count_none)
                } else {
                    activity.getString(R.string.api_count_match, min(count, apiLimit), count)
                },
                UiHelper.MUTED
            )

            if (count > apiLimit) {
                list.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.api_btn_show_more),
                        {
                            onLimitChanged.accept(apiLimit + 60)
                            populateMethods(activity, list, catalog, search, apiLimit + 60, favoritesOnly, favorites, onShowMethod, onLimitChanged)
                        },
                        false
                    )
                )
            }
        }

        @JvmStatic
        @Throws(Exception::class)
        fun renderMethodDetails(
            activity: MainActivity,
            content: LinearLayout,
            scroll: ScrollView,
            metadata: JSONObject,
            favorites: MutableSet<String>,
            uploadBytes: ByteArray?,
            onBack: Runnable,
            callback: ApiCallback
        ) {
            val methodName = metadata.getString("name")
            content.removeAllViews()
            scroll.smoothScrollTo(0, 0)

            content.addView(UiHelper.button(activity, activity.getString(R.string.api_btn_back_catalog), onBack, false))
            UiHelper.add(activity, content, methodName.substring("services/".length), 20, UiHelper.INK, true)
            UiHelper.note(activity, content, UiHelper.plain(metadata.optString("brief_description")), UiHelper.MUTED)

            val favorite = UiHelper.button(
                activity,
                if (favorites.contains(methodName)) activity.getString(R.string.api_btn_fav_remove) else activity.getString(R.string.api_btn_fav_add),
                {},
                false
            )
            favorite.setOnClickListener {
                if (!favorites.remove(methodName)) favorites.add(methodName)
                activity.getSharedPreferences("api_catalog", Context.MODE_PRIVATE)
                    .edit()
                    .putStringSet("favorites", HashSet(favorites))
                    .apply()
                favorite.text = if (favorites.contains(methodName)) {
                    activity.getString(R.string.api_btn_fav_remove)
                } else {
                    activity.getString(R.string.api_btn_fav_add)
                }
            }
            content.addView(favorite)

            val auth = metadata.getJSONObject("auth_options")
            val docs = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, docs, activity.getString(R.string.api_access_heading), 17, UiHelper.INK, true)
            UiHelper.add(
                activity,
                docs,
                activity.getString(
                    R.string.api_auth_details,
                    auth.optString("consumer"),
                    auth.optString("token"),
                    (auth.optJSONArray("scopes") ?: JSONArray()).toString(),
                    if (auth.optBoolean("administrative_only")) activity.getString(R.string.api_admin_key_required) else ""
                ),
                13,
                UiHelper.MUTED,
                false
            )
            val missing = ApiCatalog.missingScopes(metadata, activity.serviceAccountState().optString("scopes"))
            if (missing.isNotEmpty()) {
                UiHelper.note(
                    activity,
                    docs,
                    activity.getString(R.string.api_missing_scopes, missing.joinToString(" | ")),
                    Color.rgb(160, 90, 35)
                )
            } else if (activity.connected() && !activity.isDemo()) {
                UiHelper.note(activity, docs, activity.getString(R.string.api_access_available), UiHelper.GREEN)
            }
            if (metadata.optBoolean("beta")) {
                UiHelper.note(activity, docs, activity.getString(R.string.api_beta_note), UiHelper.GREEN)
            }
            if (!metadata.isNull("deprecated")) {
                UiHelper.note(
                    activity,
                    docs,
                    activity.getString(R.string.api_deprecated_note, UiHelper.plain(metadata.opt("deprecated")?.toString().orEmpty())),
                    Color.rgb(167, 99, 24)
                )
            }
            val documentation = UiHelper.disclosure(activity, docs, "api.documentation", R.string.api_documentation)
            UiHelper.add(activity, documentation, UiHelper.plain(metadata.optString("description")), 14, UiHelper.INK, false)

            val arguments = metadata.getJSONArray("arguments")
            val inputs = LinkedHashMap<String, EditText>()
            val defaults = LinkedHashMap<String, String>()
            val draft = activity.apiDraft(methodName)
            val required = HashSet<String>()
            val params = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, params, activity.getString(R.string.api_params_heading), 18, UiHelper.INK, true)

            val oauthKeys = setOf(
                "oauth_consumer_key",
                "oauth_nonce",
                "oauth_timestamp",
                "oauth_signature",
                "oauth_signature_method",
                "oauth_version",
                "oauth_token"
            )
            for (i in 0 until arguments.length()) {
                val arg = arguments.getJSONObject(i)
                val key = arg.getString("name")
                if (!oauthKeys.contains(key)) {
                    val needed = arg.optBoolean("is_required")
                    if (needed) required.add(key)
                    val defaultValue = if (arg.isNull("default_value")) "" else arg.optString("default_value")
                    UiHelper.add(
                        activity,
                        params,
                        key + (if (needed) " *" else "") +
                            (if (defaultValue.isEmpty()) "" else activity.getString(R.string.api_param_default_val, defaultValue)),
                        13,
                        UiHelper.INK,
                        true
                    )
                    if (key == "data" && arg.optString("description").contains("multipart/form-data")) {
                        val uploadBtn = UiHelper.button(
                            activity,
                            activity.getString(R.string.api_btn_select_file),
                            { callback.onSelectUploadFile(methodName) },
                            false
                        )
                        params.addView(uploadBtn)
                        UiHelper.add(activity, params, UiHelper.plain(arg.optString("description")), 12, UiHelper.MUTED, false)
                    } else {
                        val input = UiHelper.field(
                            activity,
                            params,
                            key + (if (needed) " *" else ""),
                            draft.optString(key),
                            ApiCatalog.sensitiveParameter(key)
                        )
                        inputs[key] = input
                        if (defaultValue.isNotEmpty() && !defaultValue.startsWith("(")) {
                            defaults[key] = defaultValue
                        }
                        UiHelper.watchSearch(input) { value ->
                            try {
                                draft.put(key, value)
                            } catch (_: JSONException) {
                            }
                        }
                        UiHelper.add(activity, params, UiHelper.plain(arg.optString("description")), 12, UiHelper.MUTED, false)
                    }
                }
            }
            if (defaults.isNotEmpty()) {
                params.addView(
                    UiHelper.button(
                        activity,
                        activity.getString(R.string.api_defaults_fill),
                        {
                            for ((k, v) in defaults) {
                                val field = inputs[k]
                                if (field != null && field.text.toString().isEmpty()) {
                                    field.setText(v)
                                }
                            }
                        },
                        false
                    )
                )
            }
            if (required.isEmpty()) {
                UiHelper.note(activity, params, activity.getString(R.string.api_no_required_params), UiHelper.MUTED)
            }

            UiHelper.add(activity, params, activity.getString(R.string.api_transport), 13, UiHelper.INK, true)
            val transport = Spinner(activity)
            transport.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, arrayOf("GET", "POST"))
            val multipart = methodName == "services/blobbox/put" || methodName == "services/blobbox/put_unsigned"
            transport.setSelection(draft.optInt("__transport", if (multipart) 1 else 0))
            transport.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    try {
                        draft.put("__transport", position)
                    } catch (_: JSONException) {
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            params.addView(transport)
            UiHelper.note(activity, params, activity.getString(R.string.api_transport_hint), UiHelper.MUTED)
            val currentUpload = activity.apiUpload(methodName)
            if (currentUpload != null) {
                UiHelper.note(
                    activity,
                    params,
                    activity.getString(R.string.api_upload_selected, currentUpload.size),
                    UiHelper.GREEN
                )
            }

            val result = UiHelper.text(activity, activity.getString(R.string.api_response_placeholder), 12, UiHelper.INK, false)
            result.setTextIsSelectable(true)
            result.typeface = Typeface.MONOSPACE
            var lastResponse: UsosClient.Response? = null
            val saveResponse = UiHelper.button(
                activity,
                activity.getString(R.string.api_export_response),
                { lastResponse?.let { callback.onSaveResponse(it) } },
                false
            )
            saveResponse.isEnabled = false
            val copyResponse = UiHelper.button(
                activity,
                activity.getString(R.string.api_copy_response),
                { UiHelper.copyToClipboard(activity, methodName, result.text.toString()) },
                false
            )
            copyResponse.isEnabled = false

            params.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.api_execute_btn),
                    exec@{
                        val values = LinkedHashMap<String, String>()
                        for ((key, field) in inputs) {
                            val value = field.text.toString()
                            if (value.trim().isEmpty()) {
                                if (required.contains(key)) {
                                    field.error = activity.getString(R.string.api_param_required_hint)
                                    field.requestFocus()
                                    Toast.makeText(activity, activity.getString(R.string.api_toast_fill_param, key), Toast.LENGTH_SHORT).show()
                                    return@exec
                                }
                            } else {
                                values[key] = value
                            }
                        }

                        val method = if (transport.selectedItemPosition == 0) "GET" else "POST"
                        val endpoint = metadata.optString("name")
                        val selectedFile = activity.apiUpload(methodName)
                        if (multipart && (selectedFile == null || method != "POST")) {
                            Toast.makeText(activity, R.string.api_upload_post_required, Toast.LENGTH_LONG).show()
                            return@exec
                        }

                        MaterialAlertDialogBuilder(activity)
                            .setTitle(activity.getString(R.string.api_confirm_title, method))
                            .setMessage(activity.getString(R.string.api_confirm_msg, endpoint))
                            .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                            .setPositiveButton(activity.getString(R.string.api_btn_execute)) { _, _ ->
                                lastResponse = null
                                saveResponse.isEnabled = false
                                copyResponse.isEnabled = false
                                result.setText(R.string.api_sending)
                                callback.onExecuteRequest(
                                    metadata,
                                    values,
                                    method,
                                    selectedFile,
                                    { response ->
                                        if (ApiCatalog.credentialResponse(endpoint)) {
                                            result.setText(
                                                if (endpoint.startsWith("services/cards/")) {
                                                    R.string.api_activation_hidden
                                                } else {
                                                    R.string.api_oauth_hidden
                                                }
                                            )
                                            return@onExecuteRequest
                                        }
                                        var formatted = if (response.binary()) {
                                            activity.getString(R.string.api_binary_response, response.mime, response.bytes.size)
                                        } else {
                                            String(response.bytes, 0, min(response.bytes.size, 100000), StandardCharsets.UTF_8)
                                        }
                                        try {
                                            val parsed = if (response.binary() || response.bytes.size > 100000) {
                                                null
                                            } else {
                                                JSONTokener(formatted).nextValue()
                                            }
                                            if (parsed is JSONObject) formatted = parsed.toString(2)
                                            else if (parsed is JSONArray) formatted = parsed.toString(2)
                                        } catch (_: JSONException) {
                                        }
                                        lastResponse = response
                                        saveResponse.isEnabled = true
                                        copyResponse.isEnabled = !response.binary()
                                        if (!response.binary() && (response.bytes.size > 100000 || formatted.length > 100000)) {
                                            formatted = formatted.substring(0, min(100000, formatted.length)) +
                                                activity.getString(R.string.api_preview_limited)
                                        }
                                        result.text = formatted
                                    },
                                    { message -> result.text = activity.getString(R.string.api_failed, message) }
                                )
                            }
                            .show()
                    },
                    true
                )
            )

            val output = UiHelper.section(activity, content, Color.WHITE)
            UiHelper.add(activity, output, activity.getString(R.string.api_response_title), 17, UiHelper.INK, true)
            output.addView(result)
            output.addView(saveResponse)
            output.addView(copyResponse)
            if (metadata.optString("returns").isNotEmpty()) {
                val fields = UiHelper.disclosure(activity, content, "api.resultFields", R.string.api_result_fields)
                UiHelper.add(activity, fields, UiHelper.plain(metadata.optString("returns")), 14, UiHelper.MUTED, false)
            }
            if (metadata.optString("errors").isNotEmpty()) {
                val errors = UiHelper.disclosure(activity, content, "api.errors", R.string.api_errors_reference)
                UiHelper.add(activity, errors, UiHelper.plain(metadata.optString("errors")), 14, UiHelper.MUTED, false)
            }

            content.addView(
                UiHelper.button(
                    activity,
                    activity.getString(R.string.api_btn_online_docs),
                    { UiHelper.browser(activity, WebLinks.apiDocumentation(metadata.optString("name"))) },
                    false
                )
            )

            UiHelper.note(
                activity,
                content,
                activity.getString(R.string.api_limits_note),
                UiHelper.MUTED
            )
        }
    }
}

