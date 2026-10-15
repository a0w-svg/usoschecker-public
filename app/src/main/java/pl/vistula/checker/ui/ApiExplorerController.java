package pl.vistula.checker.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import pl.vistula.checker.BuildConfig;
import pl.vistula.checker.MainActivity;
import pl.vistula.checker.R;
import pl.vistula.checker.UsosClient;
import pl.vistula.checker.core.ApiCatalog;

/**
 * Controller for rendering the USOS API interactive catalog and tester.
 */
public final class ApiExplorerController {

    private ApiExplorerController() {}

    public interface ApiCallback {
        void onExecuteRequest(JSONObject metadata, Map<String, String> values, String method, byte[] uploadBytes, java.util.function.Consumer<UsosClient.Response> onSuccess, java.util.function.Consumer<String> onFailure);
        void onSelectUploadFile(String currentMethod);
        void onSaveResponse(UsosClient.Response response);
    }

    public static void renderCatalog(MainActivity activity, LinearLayout content, JSONArray catalog,
                                     String apiQuery, int apiLimit, boolean favoritesOnly,
                                     Set<String> favorites,
                                     java.util.function.Consumer<String> onQueryChanged,
                                     java.util.function.Consumer<Integer> onLimitChanged,
                                     java.util.function.Consumer<Boolean> onFavoritesChanged,
                                     java.util.function.Consumer<String> onShowMethod) {
        UiHelper.title(activity, content,
            activity.getString(R.string.api_title),
            activity.getString(R.string.api_subtitle_methods, catalog.length()));

        UiHelper.note(activity, content,
            activity.getString(R.string.api_offline_note),
            UiHelper.MUTED);

        UiHelper.chips(activity, content,
            new String[]{"all", "favorites"},
            new String[]{activity.getString(R.string.api_all_methods), activity.getString(R.string.api_fav_filter)},
            favoritesOnly ? "favorites" : "all",
            value -> onFavoritesChanged.accept(value.equals("favorites")));
        Map<String, Integer> modules = ApiCatalog.modules(catalog);
        UiHelper.note(activity, content, activity.getString(R.string.api_modules_count, modules.size(), catalog.length()), UiHelper.MUTED);
        java.util.List<String> moduleValues = new java.util.ArrayList<>(), moduleLabels = new java.util.ArrayList<>();
        moduleValues.add(""); moduleLabels.add(activity.getString(R.string.api_modules_all));
        for (Map.Entry<String, Integer> module : modules.entrySet()) {
            moduleValues.add(module.getKey());
            moduleLabels.add(ApiCatalog.label(module.getKey()) + " · " + module.getKey() + " (" + module.getValue() + ")");
        }
        Spinner modulePicker = new Spinner(activity);
        modulePicker.setContentDescription(activity.getString(R.string.api_module_choose));
        modulePicker.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, moduleLabels));
        modulePicker.setSelection(Math.max(0, moduleValues.indexOf(activity.getApiModule())));
        content.addView(modulePicker, new LinearLayout.LayoutParams(-1, UiHelper.dp(activity, 56)));
        modulePicker.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (modulePicker.isAttachedToWindow() && !moduleValues.get(position).equals(activity.getApiModule())) activity.setApiModule(moduleValues.get(position));
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        UiHelper.heading(activity, content, activity.getString(R.string.api_key_services_heading));
        String[] quickMethods = {
            "services/users/user", "services/grades/terms2", "services/tt/student",
            "services/payments/user_accounts", "services/payments/user_payments", "services/theses/user", "services/crstests/user", "services/terms/terms",
            "services/cards/mcard2", "services/adm_documents/user_documents"
        };
        int[] quickLabelRes = {
            R.string.api_quick_profile, R.string.api_quick_grades, R.string.api_quick_schedule,
            R.string.api_quick_accounts, R.string.api_quick_payments, R.string.api_quick_theses, R.string.api_quick_tests, R.string.api_quick_terms,
            R.string.cards_title, R.string.documents_title
        };
        LinearLayout quickScrollLayout = new LinearLayout(activity);
        HorizontalScrollView quickScroll = new HorizontalScrollView(activity);
        quickScroll.setHorizontalScrollBarEnabled(false);
        quickScroll.addView(quickScrollLayout);
        for (int i = 0; i < quickMethods.length; i++) {
            final String qm = quickMethods[i];
            Button qb = UiHelper.button(activity, activity.getString(quickLabelRes[i]), () -> onShowMethod.accept(qm), false);
            qb.setTextSize(12);
            qb.setSingleLine(true);
            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-2, -2);
            qp.rightMargin = UiHelper.dp(activity, 6);
            quickScrollLayout.addView(qb, qp);
        }
        LinearLayout.LayoutParams qsp = new LinearLayout.LayoutParams(-1, -2);
        qsp.bottomMargin = UiHelper.dp(activity, 12);
        content.addView(quickScroll, qsp);

        UiHelper.note(activity, content, activity.getString(R.string.api_search_alias_hint), UiHelper.MUTED);
        EditText search = UiHelper.searchField(activity, content, activity.getString(R.string.api_search_hint), apiQuery, null, null);
        final LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        populateMethods(activity, list, catalog, apiQuery, apiLimit, favoritesOnly, favorites, onShowMethod, onLimitChanged);

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                onQueryChanged.accept(s.toString());
                populateMethods(activity, list, catalog, s.toString(), apiLimit, favoritesOnly, favorites, onShowMethod, onLimitChanged);
            }
            public void afterTextChanged(Editable e) {}
        });
    }

    private static void populateMethods(MainActivity activity, LinearLayout list, JSONArray catalog,
                                        String search, int apiLimit, boolean favoritesOnly,
                                        Set<String> favorites,
                                        java.util.function.Consumer<String> onShowMethod,
                                        java.util.function.Consumer<Integer> onLimitChanged) {
        list.removeAllViews();
        String needle = search.trim().toLowerCase(Locale.ROOT);
        int count = 0;
        int shown = 0;

        try {
            for (int i = 0; i < catalog.length(); ++i) {
                JSONObject entry = catalog.getJSONObject(i);
                String name = entry.getString("name");
                String brief = entry.optString("brief_description").trim();
                if ((!favoritesOnly || favorites.contains(name)) && ApiCatalog.matches(entry, activity.getApiModule(), search)) {
                    ++count;
                    if (shown++ < apiLimit) {
                        Button b = UiHelper.button(activity, name.substring("services/".length()) + "\n" + brief, () -> onShowMethod.accept(name), false);
                        b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                        b.setTextSize(13.0f);
                        list.addView(b, new LinearLayout.LayoutParams(-1, -2));
                    }
                }
            }
        } catch (JSONException ignored) {}

        UiHelper.note(activity, list,
            count == 0 ? activity.getString(R.string.api_count_none) : activity.getString(R.string.api_count_match, Math.min(count, apiLimit), count),
            UiHelper.MUTED);

        if (count > apiLimit) {
            list.addView(UiHelper.button(activity, activity.getString(R.string.api_btn_show_more), () -> {
                onLimitChanged.accept(apiLimit + 60);
                populateMethods(activity, list, catalog, search, apiLimit + 60, favoritesOnly, favorites, onShowMethod, onLimitChanged);
            }, false));
        }
    }

    public static void renderMethodDetails(MainActivity activity, LinearLayout content, ScrollView scroll,
                                          JSONObject metadata, Set<String> favorites,
                                          byte[] uploadBytes,
                                          Runnable onBack,
                                          ApiCallback callback) throws Exception {
        String methodName = metadata.getString("name");
        content.removeAllViews();
        scroll.smoothScrollTo(0, 0);

        content.addView(UiHelper.button(activity, activity.getString(R.string.api_btn_back_catalog), onBack, false));
        UiHelper.title(activity, content, methodName.substring("services/".length()), UiHelper.plain(metadata.optString("brief_description")));

        Button favorite = UiHelper.button(activity, favorites.contains(methodName) ? activity.getString(R.string.api_btn_fav_remove) : activity.getString(R.string.api_btn_fav_add), () -> {}, false);
        favorite.setOnClickListener(v -> {
            if (!favorites.remove(methodName)) favorites.add(methodName);
            activity.getSharedPreferences("api_catalog", Context.MODE_PRIVATE).edit().putStringSet("favorites", new HashSet<>(favorites)).apply();
            favorite.setText(favorites.contains(methodName) ? activity.getString(R.string.api_btn_fav_remove) : activity.getString(R.string.api_btn_fav_add));
        });
        content.addView(favorite);

        JSONObject auth = metadata.getJSONObject("auth_options");
        LinearLayout docs = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, docs, activity.getString(R.string.api_access_heading), 17, UiHelper.INK, true);
        UiHelper.add(activity, docs, activity.getString(R.string.api_auth_details, auth.optString("consumer"), auth.optString("token"), String.valueOf(auth.optJSONArray("scopes")), (auth.optBoolean("administrative_only") ? activity.getString(R.string.api_admin_key_required) : "")), 13, UiHelper.MUTED, false);
        java.util.List<String> missing = ApiCatalog.missingScopes(metadata, activity.serviceAccountState().optString("scopes"));
        if (!missing.isEmpty()) UiHelper.note(activity, docs, activity.getString(R.string.api_missing_scopes, String.join(" | ", missing)), Color.rgb(160, 90, 35));
        else if (activity.connected() && !activity.isDemo()) UiHelper.note(activity, docs, activity.getString(R.string.api_access_available), UiHelper.GREEN);
        if (metadata.optBoolean("beta")) {
            UiHelper.note(activity, docs, activity.getString(R.string.api_beta_note), UiHelper.GREEN);
        }
        if (!metadata.isNull("deprecated")) {
            UiHelper.note(activity, docs, activity.getString(R.string.api_deprecated_note, UiHelper.plain(metadata.opt("deprecated").toString())), Color.rgb(167, 99, 24));
        }
        UiHelper.add(activity, docs, UiHelper.plain(metadata.optString("description")), 14, UiHelper.INK, false);

        JSONArray arguments = metadata.getJSONArray("arguments");
        LinkedHashMap<String, EditText> inputs = new LinkedHashMap<>();
        LinkedHashMap<String, String> defaults = new LinkedHashMap<>();
        JSONObject draft = activity.apiDraft(methodName);
        Set<String> required = new HashSet<>();
        LinearLayout params = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, params, activity.getString(R.string.api_params_heading), 18, UiHelper.INK, true);

        for (int i = 0; i < arguments.length(); ++i) {
            JSONObject arg = arguments.getJSONObject(i);
            String key = arg.getString("name");
            if (!Set.of("oauth_consumer_key", "oauth_nonce", "oauth_timestamp", "oauth_signature", "oauth_signature_method", "oauth_version", "oauth_token").contains(key)) {
                boolean needed = arg.optBoolean("is_required");
                if (needed) required.add(key);
                String defaultValue = arg.isNull("default_value") ? "" : arg.optString("default_value");
                UiHelper.add(activity, params, key + (needed ? " *" : "") + (defaultValue.isEmpty() ? "" : activity.getString(R.string.api_param_default_val, defaultValue)), 13, UiHelper.INK, true);
                if (key.equals("data") && arg.optString("description").contains("multipart/form-data")) {
                    Button uploadBtn = UiHelper.button(activity, activity.getString(R.string.api_btn_select_file), () -> callback.onSelectUploadFile(methodName), false);
                    params.addView(uploadBtn);
                    UiHelper.add(activity, params, UiHelper.plain(arg.optString("description")), 12, UiHelper.MUTED, false);
                } else {
                    EditText input = UiHelper.field(activity, params, key + (needed ? " *" : ""), draft.optString(key), ApiCatalog.sensitiveParameter(key));
                    inputs.put(key, input);
                    if (!defaultValue.isEmpty() && !defaultValue.startsWith("(")) defaults.put(key, defaultValue);
                    UiHelper.watchSearch(input, value -> { try { draft.put(key, value); } catch (JSONException ignored) {} });
                    UiHelper.add(activity, params, UiHelper.plain(arg.optString("description")), 12, UiHelper.MUTED, false);
                }
            }
        }
        if (!defaults.isEmpty()) params.addView(UiHelper.button(activity, activity.getString(R.string.api_defaults_fill), () -> {
            for (Map.Entry<String, String> value : defaults.entrySet()) if (inputs.get(value.getKey()).getText().toString().isEmpty()) inputs.get(value.getKey()).setText(value.getValue());
        }, false));
        if (required.isEmpty()) UiHelper.note(activity, params, activity.getString(R.string.api_no_required_params), UiHelper.MUTED);

        UiHelper.add(activity, params, activity.getString(R.string.api_transport), 13, UiHelper.INK, true);
        Spinner transport = new Spinner(activity);
        transport.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, new String[]{"GET", "POST"}));
        boolean multipart = methodName.equals("services/blobbox/put") || methodName.equals("services/blobbox/put_unsigned");
        transport.setSelection(draft.optInt("__transport", multipart ? 1 : 0));
        transport.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                try { draft.put("__transport", position); } catch (JSONException ignored) {}
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        params.addView(transport);
        UiHelper.note(activity, params, activity.getString(R.string.api_transport_hint), UiHelper.MUTED);
        if (activity.apiUpload(methodName) != null) UiHelper.note(activity, params,
            activity.getString(R.string.api_upload_selected, activity.apiUpload(methodName).length), UiHelper.GREEN);

        TextView result = UiHelper.text(activity, activity.getString(R.string.api_response_placeholder), 12, UiHelper.INK, false);
        result.setTextIsSelectable(true);
        result.setTypeface(Typeface.MONOSPACE);
        UsosClient.Response[] lastResponse = {null};
        Button saveResponse = UiHelper.button(activity, activity.getString(R.string.api_export_response), () -> {
            if (lastResponse[0] != null) callback.onSaveResponse(lastResponse[0]);
        }, false);
        saveResponse.setEnabled(false);
        Button copyResponse = UiHelper.button(activity, activity.getString(R.string.api_copy_response), () ->
            UiHelper.copyToClipboard(activity, methodName, result.getText().toString()), false);
        copyResponse.setEnabled(false);

        params.addView(UiHelper.button(activity, activity.getString(R.string.api_execute_btn), () -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> input : inputs.entrySet()) {
                String value = input.getValue().getText().toString();
                if (value.trim().isEmpty()) {
                    if (required.contains(input.getKey())) {
                        input.getValue().setError(activity.getString(R.string.api_param_required_hint));
                        input.getValue().requestFocus();
                        Toast.makeText(activity, activity.getString(R.string.api_toast_fill_param, input.getKey()), Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else {
                    values.put(input.getKey(), value);
                }
            }

            String method = transport.getSelectedItemPosition() == 0 ? "GET" : "POST";
            String endpoint = metadata.optString("name");
            byte[] selectedFile = activity.apiUpload(methodName);
            if (multipart && (selectedFile == null || !method.equals("POST"))) {
                Toast.makeText(activity, R.string.api_upload_post_required, Toast.LENGTH_LONG).show(); return;
            }

            new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.api_confirm_title, method))
                .setMessage(activity.getString(R.string.api_confirm_msg, endpoint))
                .setNegativeButton(activity.getString(R.string.btn_cancel), null)
                .setPositiveButton(activity.getString(R.string.api_btn_execute), (dialog, which) -> {
                    lastResponse[0] = null; saveResponse.setEnabled(false); copyResponse.setEnabled(false);
                    result.setText(R.string.api_sending);
                    callback.onExecuteRequest(metadata, values, method, selectedFile,
                        response -> {
                            if (ApiCatalog.credentialResponse(endpoint)) {
                                result.setText(endpoint.startsWith("services/cards/") ? R.string.api_activation_hidden : R.string.api_oauth_hidden);
                                return;
                            }
                            String formatted = response.binary()
                                ? activity.getString(R.string.api_binary_response, response.mime, response.bytes.length)
                                : new String(response.bytes, 0, Math.min(response.bytes.length, 100000), StandardCharsets.UTF_8);
                            try {
                                Object parsed = response.binary() || response.bytes.length > 100000 ? null : new JSONTokener(formatted).nextValue();
                                if (parsed instanceof JSONObject) formatted = ((JSONObject) parsed).toString(2);
                                else if (parsed instanceof JSONArray) formatted = ((JSONArray) parsed).toString(2);
                            } catch (JSONException ignored) {}
                            lastResponse[0] = response; saveResponse.setEnabled(true); copyResponse.setEnabled(!response.binary());
                            if (!response.binary() && (response.bytes.length > 100000 || formatted.length() > 100000)) {
                                formatted = formatted.substring(0, Math.min(100000, formatted.length())) + activity.getString(R.string.api_preview_limited);
                            }
                            result.setText(formatted);
                        },
                        message -> result.setText(activity.getString(R.string.api_failed, message)));
                })
                .show();
        }, true));

        LinearLayout output = UiHelper.card(activity, content, Color.WHITE);
        UiHelper.add(activity, output, activity.getString(R.string.api_response_title), 17, UiHelper.INK, true);
        output.addView(result);
        output.addView(saveResponse); output.addView(copyResponse);
        if (!metadata.optString("returns").isEmpty()) {
            LinearLayout fields = UiHelper.card(activity, content, Color.WHITE);
            UiHelper.add(activity, fields, activity.getString(R.string.api_result_fields), 18, UiHelper.INK, true);
            UiHelper.add(activity, fields, UiHelper.plain(metadata.optString("returns")), 14, UiHelper.MUTED, false);
        }
        if (!metadata.optString("errors").isEmpty()) {
            LinearLayout errors = UiHelper.card(activity, content, Color.WHITE);
            UiHelper.add(activity, errors, activity.getString(R.string.api_errors_reference), 18, UiHelper.INK, true);
            UiHelper.add(activity, errors, UiHelper.plain(metadata.optString("errors")), 14, UiHelper.MUTED, false);
        }

        content.addView(UiHelper.button(activity, activity.getString(R.string.api_btn_online_docs),
            () -> UiHelper.browser(activity, "https://usosapps.vistula.edu.pl/developers/api/" + metadata.optString("name").substring("services/".length(), metadata.optString("name").lastIndexOf('/')) + "/"), false));

        UiHelper.note(activity, content,
            activity.getString(R.string.api_limits_note),
            UiHelper.MUTED);
    }
}
