package pl.vistula.checker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import androidx.core.app.NotificationManagerCompat;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import pl.vistula.checker.core.Change;
import pl.vistula.checker.core.Record;
import pl.vistula.checker.core.Snapshot;
import pl.vistula.checker.core.UsosParser;

public final class MainActivity extends Activity {
    private static final int INK = Color.rgb(16, 45, 72), GREEN = Color.rgb(21, 123, 100);
    private static final int MUTED = Color.rgb(99, 115, 132), BG = Color.rgb(245, 247, 250);
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private JSONObject state = new JSONObject();
    private JSONArray catalog = new JSONArray();
    private LinearLayout root, content, navigation;
    private ScrollView scroll;
    private TextView progress;
    private boolean demo, demoChanged, busy;
    private int tab;
    private String filter = "wszystkie", dataFilter = "ocena";
    private String currentMethod;
    private byte[] uploadBytes;
    private String uploadMethod;
    private UsosClient.Response exportResponse;
    private Button uploadButton;
    private Repository repository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (!busy && !demo && currentMethod == null && tab != 4 && tab != 3) load();
            handler.postDelayed(this, 30000);
        }
    };
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        repository = new Repository(this);
        if (bundle != null) {
            demo = bundle.getBoolean("demo"); demoChanged = bundle.getBoolean("demoChanged"); tab = bundle.getInt("tab");
        } else if (getIntent().getBooleanExtra("showHistory", false)) tab = 1;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            root.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        TextView brand = text("VISTULA  /  ZMIANY", 13, INK, true); brand.setPadding(dp(24), dp(20), dp(24), dp(12)); root.addView(brand);
        progress = text("Trwa wykonywanie operacji…", 12, GREEN, false);
        progress.setPadding(dp(24), 0, dp(24), dp(8)); progress.setVisibility(View.GONE); root.addView(progress);
        scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20), dp(12), dp(20), dp(24));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new LinearLayout(this); navigation.setPadding(dp(8), dp(8), dp(8), dp(8)); navigation.setBackgroundColor(Color.WHITE);
        root.addView(navigation); setContentView(root);
        task(() -> {
            catalog = new JSONArray(asset("api/index.json"));
            return repository.read();
        }, result -> { state = result; ensureSchedule(); render(); });
    }
    @Override public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out); out.putBoolean("demo", demo); out.putBoolean("demoChanged", demoChanged); out.putInt("tab", tab);
    }
    @Override protected void onResume() { super.onResume(); handler.postDelayed(refresh, 30000); if (content != null && !busy && tab != 4 && tab != 3) load(); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(refresh); }
    @Override protected void onDestroy() { super.onDestroy(); io.shutdown(); }
    private void ensureSchedule() {
        if (state.optBoolean("enabled") && connected()) SyncWorker.schedule(this, state.optLong("interval", 30));
    }
    private boolean connected() { return !state.optString("token").isEmpty(); }
    private JSONObject display() throws JSONException { return demo ? Demo.state(demoChanged) : state; }
    private void load() { task(repository::read, s -> { state = s; render(); }); }
    private interface Job<T> { T run() throws Exception; }
    private interface Done<T> { void accept(T value) throws Exception; }
    private <T> void task(Job<T> job, Done<T> done) {
        if (busy || isDestroyed()) return;
        busy = true; updateBusy(true);
        io.execute(() -> {
            try {
                T result = job.run();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false; updateBusy(false);
                    try { done.accept(result); } catch (Exception e) { error(Repository.message(e)); }
                });
            } catch (Exception e) {
                JSONObject latest;
                try { latest = repository.read(); } catch (Exception ignored) { latest = state; }
                JSONObject recovered = latest;
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    busy = false; state = recovered; updateBusy(false);
                    if (tab == 0 || tab == 1 || tab == 2) render();
                    error(Repository.message(e));
                });
            }
        });
    }
    private void updateBusy(boolean value) {
        setButtons(root, !value);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        root.setAlpha(value ? 0.65f : 1f);
    }
    private void setButtons(View v, boolean enabled) {
        if (v instanceof Button || v instanceof Switch || v instanceof Spinner || v instanceof EditText) v.setEnabled(enabled);
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) setButtons(((ViewGroup) v).getChildAt(i), enabled);
    }
    private void render() {
        if (currentMethod != null || isDestroyed()) return;
        content.removeAllViews(); navigation.removeAllViews();
        String[] tabs = {"Start", "Zmiany", "Dane", "API", "Konto"};
        for (int i = 0; i < tabs.length; i++) {
            final int target = i;
            Button b = button(tabs[i], () -> { tab = target; currentMethod = null; render(); scroll.smoothScrollTo(0, 0); }, i == tab);
            b.setTextSize(11); b.setPadding(0, 0, 0, 0); b.setMinWidth(0);
            navigation.addView(b, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        try {
            if (demo) note(content, "TRYB DEMO · przykładowe dane, bez połączenia z USOS", GREEN);
            switch (tab) {
                case 0: home(); break;
                case 1: history(); break;
                case 2: data(); break;
                case 3: api(); break;
                case 4: account(); break;
            }
        } catch (Exception e) { note(content, Repository.message(e), Color.rgb(167, 49, 49)); }
        updateBusy(busy);
    }
    private void home() throws Exception {
        JSONObject d = display();
        LinearLayout hero = card(content, INK);
        add(hero, "TWÓJ ASYSTENT UCZELNI", 11, Color.rgb(124, 219, 192), true);
        add(hero, "USOS pod\nkontrolą.", 34, Color.WHITE, true);
        add(hero, "Nowe oceny i zmiany w planie.\nWszystko w jednym miejscu.", 15, Color.rgb(209, 221, 233), false);
        heading("Stan monitorowania");
        LinearLayout status = card(content, Color.WHITE);
        boolean active = connected() && state.optBoolean("enabled") && !demo;
        add(status, demo ? "Podgląd demonstracyjny" : active ? "Monitorowanie włączone" : connected() ? "Monitorowanie wstrzymane" : "Połącz konto USOS", 19, INK, true);
        add(status, demo ? "Te dane służą do sprawdzenia działania aplikacji."
            : connected() ? state.optString("user", "Konto USOS") : "Zaloguj się przez stronę uczelni, aby śledzić swoje dane.", 14, MUTED, false);
        add(status, "Ostatnie udane sprawdzenie\n" + timestamp(d.optLong("successAt")), 13, MUTED, false);
        if (!state.optString("error").isEmpty() && !demo) note(status, state.optString("error"), Color.rgb(167, 49, 49));
        if (demo || connected()) status.addView(button(demo ? "Symuluj zmiany" : "Sprawdź teraz", this::sync, true));
        else status.addView(button("Połącz konto", () -> { tab = 4; render(); }, true));
        if (!demo && !connected()) status.addView(button("Zobacz wersję demonstracyjną", () -> { demo = true; render(); }, false));
        JSONArray rows = d.optJSONArray("history");
        heading("Ostatnie zmiany");
        if (rows == null || rows.length() == 0) empty("Jeszcze bez zmian", "Pierwsze pobranie zapisuje punkt odniesienia. Kolejne różnice zobaczysz tutaj.");
        else {
            for (int i = 0; i < Math.min(3, rows.length()); i++) changeCard(Change.from(rows.getJSONObject(i)));
            content.addView(button("Otwórz całą historię", () -> { tab = 1; render(); }, false));
        }
        note(content, "Plan obejmuje najbliższe 28 dni. Sprawdzanie w tle co " + state.optLong("interval", 30)
            + " min może być opóźnione przez Androida. Czas wykrycia nie oznacza czasu publikacji.", MUTED);
    }
    private void sync() {
        if (demo) { demoChanged = true; tab = 1; render(); return; }
        task(() -> { List<Change> changes = repository.sync(true); SyncWorker.notifyChanges(this, changes); return repository.read(); },
            result -> { state = result; render(); Toast.makeText(this, "Sprawdzono oceny i plan zajęć", Toast.LENGTH_SHORT).show(); });
    }
    private void history() throws Exception {
        title("Historia zmian", "Kiedy aplikacja zauważyła różnicę w USOS.");
        chips(new String[]{"wszystkie", "ocena", "plan"}, new String[]{"Wszystkie", "Oceny", "Plan"}, filter, value -> { filter = value; render(); });
        JSONArray history = display().optJSONArray("history"); int count = 0;
        if (history != null) for (int i = 0; i < history.length(); i++) {
            Change c = Change.from(history.getJSONObject(i));
            if (filter.equals("wszystkie") || filter.equals(c.kind)) { changeCard(c); count++; }
        }
        if (count == 0) empty("Brak zapisanych zmian", "Zmiany pojawią się po porównaniu kolejnych pobrań. Historia przechowuje ostatnie 200 wpisów.");
        if (demo) content.addView(button("Symuluj zmianę oceny i sali", this::sync, true));
    }
    private void changeCard(Change c) {
        LinearLayout box = card(content, Color.WHITE);
        add(box, (c.kind.equals("ocena") ? "OCENA" : "PLAN ZAJĘĆ") + "  ·  " + c.action, 11, GREEN, true);
        add(box, c.title, 18, INK, true);
        if (!c.before.isEmpty()) add(box, "Wcześniej: " + c.before, 14, MUTED, false);
        if (!c.after.isEmpty()) add(box, "Teraz: " + c.after, 14, INK, false);
        add(box, "Wykryto: " + timestamp(c.detectedAt), 12, MUTED, false);
        if (!c.sourceTime.isEmpty()) add(box, "Modyfikacja w USOS: " + c.sourceTime + " (Warszawa)", 12, MUTED, false);
    }
    private void data() throws Exception {
        title("Twoje dane", "Ostatni pomyślnie pobrany zapis z USOS.");
        chips(new String[]{"ocena", "plan"}, new String[]{"Oceny", "Plan · 28 dni"}, dataFilter, value -> { dataFilter = value; render(); });
        JSONObject raw = display().optJSONObject("snapshot");
        if (raw == null) { empty("Brak pobranych danych", "Połącz konto i wybierz „Sprawdź teraz”."); return; }
        Snapshot s = Snapshot.from(raw); int count = 0;
        List<Record> rows = new ArrayList<>(s.records.values());
        rows.sort(Comparator.comparing((Record r) -> r.kind.equals("plan") ? r.value : r.title));
        for (Record r : rows) if (r.kind.equals(dataFilter)) {
            LinearLayout box = card(content, Color.WHITE);
            add(box, r.title, 17, INK, true); add(box, r.value, 15, INK, false);
            if (!r.sourceTime.isEmpty()) add(box, "Modyfikacja w USOS: " + r.sourceTime, 12, MUTED, false);
            count++;
        }
        if (count == 0) empty("Brak danych w tym zakresie", "USOS nie zwrócił żadnych wpisów dla tej kategorii.");
    }
    private void account() {
        title("Konto i ustawienia", "Akademia Finansów i Biznesu Vistula");
        if (demo) content.addView(button("Zakończ tryb demonstracyjny", () -> { demo = false; render(); }, true));
        if (connected()) {
            LinearLayout box = card(content, Color.WHITE);
            add(box, state.optString("user", "Konto USOS"), 19, INK, true);
            add(box, "Zgoda OAuth: " + state.optString("scopes", "grades|studies|offline_access"), 13, MUTED, false);
            Switch toggle = new Switch(this); toggle.setText(R.string.background_check); toggle.setTextColor(INK);
            toggle.setChecked(state.optBoolean("enabled")); box.addView(toggle);
            toggle.setOnCheckedChangeListener((v, checked) -> task(() -> {
                repository.update(s -> s.put("enabled", checked)); return repository.read();
            }, s -> { state = s; if (checked) SyncWorker.schedule(this, state.optLong("interval", 30)); else SyncWorker.cancel(this); render(); }));
            add(box, "Częstotliwość", 14, MUTED, true);
            Spinner interval = new Spinner(this);
            interval.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"15 minut", "30 minut", "1 godzina", "2 godziny"}));
            long[] values = {15, 30, 60, 120};
            long selected = state.optLong("interval", 30); int position = 1;
            for (int i = 0; i < values.length; i++) if (selected == values[i]) position = i;
            interval.setSelection(position); box.addView(interval);
            box.addView(button("Zapisz częstotliwość", () -> task(() -> {
                repository.update(s -> s.put("interval", values[interval.getSelectedItemPosition()])); return repository.read();
            }, s -> { state = s; ensureSchedule(); render(); }), false));
            box.addView(button("Odłącz konto i usuń lokalne dane", () -> new AlertDialog.Builder(this)
                .setTitle("Odłączyć konto?").setMessage("Tokeny, zapis ocen, plan i historia zostaną usunięte z tego telefonu. Zgodę dla aplikacji możesz odwołać również w USOSapps.")
                .setNegativeButton("Anuluj", null).setPositiveButton("Odłącz", (dialog, which) -> task(() -> {
                    SyncWorker.cancel(this); repository.disconnect(); return repository.read();
                }, s -> { state = s; demo = false; render(); })).show(), false));
        } else {
            LinearLayout box = card(content, Color.WHITE);
            add(box, "Połącz z USOS", 20, INK, true);
            add(box, "Wpisz klucz aplikacji z USOSapps. Hasło uczelniane podajesz wyłącznie na stronie uczelni w przeglądarce.", 14, MUTED, false);
            EditText key = field(box, "Consumer Key", "", false);
            EditText secret = field(box, "Consumer Secret", "", true);
            EditText scopes = field(box, "Zakresy zgody OAuth (oddzielone |)", state.optString("scopes", "grades|studies|offline_access"), false);
            add(box, "Monitor wymaga grades, studies i offline_access. Inne zakresy dodaj tylko dla metod, których chcesz używać w katalogu API.", 12, MUTED, false);
            box.addView(button("1. Zaloguj w przeglądarce", () -> {
                String k = key.getText().toString().trim(), sec = secret.getText().toString().trim(), sc = scopes.getText().toString().trim();
                if (k.isEmpty() || sec.isEmpty()) { error("Wpisz Consumer Key i Consumer Secret. Rejestrację klucza możesz wykonać później."); return; }
                Set<String> requested = new HashSet<>(Arrays.asList(sc.split("\\|")));
                if (!requested.containsAll(List.of("grades", "studies", "offline_access"))) { error("Dodaj wymagane zakresy: grades|studies|offline_access."); return; }
                task(() -> { repository.begin(k, sec, sc); return repository.read(); }, s -> {
                    state = s; render(); browser(UsosClient.BASE + "services/oauth/authorize?oauth_token="
                        + pl.vistula.checker.core.OAuth.encode(s.getString("requestToken")) + "&interactivity=confirm_user");
                });
            }, true));
            if (!state.optString("requestToken").isEmpty()) {
                add(box, "Po zaakceptowaniu zgody skopiuj kod PIN z USOS i wróć do aplikacji.", 14, MUTED, false);
                EditText pin = field(box, "PIN autoryzacji z USOS", "", true);
                box.addView(button("2. Potwierdź PIN i połącz", () -> {
                    String value = pin.getText().toString().trim();
                    if (value.isEmpty()) { error("Wpisz kod PIN wyświetlony przez USOS."); return; }
                    task(() -> { repository.finish(value); return repository.read(); }, s -> {
                        state = s; demo = false; tab = 0; ensureSchedule(); render();
                        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
                        sync();
                    });
                }, true));
                box.addView(button("Wróć do autoryzacji w przeglądarce", () -> browser(UsosClient.BASE + "services/oauth/authorize?oauth_token="
                    + pl.vistula.checker.core.OAuth.encode(state.optString("requestToken")) + "&interactivity=confirm_user"), false));
            }
            content.addView(button("Uruchom tryb demonstracyjny", () -> { demo = true; tab = 0; render(); }, false));
        }
        LinearLayout notices = card(content, Color.WHITE);
        add(notices, "Powiadomienia", 18, INK, true);
        add(notices, NotificationManagerCompat.from(this).areNotificationsEnabled() ? "System zezwala na powiadomienia." : "Powiadomienia są wyłączone. Historia nadal zapisuje zmiany.", 14, MUTED, false);
        notices.addView(button("Ustawienia powiadomień", this::requestNotifications, false));
        add(notices, "Tokeny i historia są szyfrowane na telefonie. Aplikacja nie przechowuje hasła do uczelni i nie ma własnego serwera.", 13, MUTED, false);
        add(notices, "Czas prezentowany w strefie Europe/Warsaw. Historia: 200 wpisów. Plan: 28 dni. Oceny: semestry zwrócone dla konta przez USOS.", 12, MUTED, false);
        content.addView(button("Otwórz USOSapps Vistuli", () -> browser(UsosClient.BASE), false));
    }
    private void api() {
        title("Katalog USOS API", catalog.length() + " metod · oficjalna dokumentacja Vistuli");
        note(content, "Dokumentacja jest dostępna offline. Wykonanie żądania wymaga internetu oraz uprawnień wskazanych przy metodzie. Tryb demo nie podpisuje żądań przykładowym kontem.", MUTED);
        EditText search = field(content, "Szukaj metody, modułu lub opisu…", "", false);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
        populateMethods(list, "");
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { populateMethods(list, s.toString()); }
            public void afterTextChanged(Editable e) {}
        });
    }
    private void populateMethods(LinearLayout list, String search) {
        list.removeAllViews(); String needle = search.toLowerCase(Locale.ROOT); int count = 0, shown = 0;
        try {
            for (int i = 0; i < catalog.length(); i++) {
                JSONObject entry = catalog.getJSONObject(i);
                String name = entry.getString("name"), brief = entry.optString("brief_description").trim();
                if (!(name + " " + brief).toLowerCase(Locale.ROOT).contains(needle)) continue;
                count++;
                if (shown++ >= 60) continue;
                Button b = button(name.substring("services/".length()) + "\n" + brief, () -> showMethod(name), false);
                b.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); b.setTextSize(13);
                list.addView(b, new LinearLayout.LayoutParams(-1, -2));
            }
        } catch (JSONException e) { error("Nie można odczytać katalogu API."); }
        note(list, count == 0 ? "Brak pasujących metod." : "Pasujące metody: " + count + (count > 60 ? ". Wyświetlono 60; zawęź wyszukiwanie." : "."), MUTED);
    }
    private void showMethod(String name) {
        task(() -> new JSONObject(asset("api/" + name.replace("/", "__") + ".json")), this::methodView);
    }
    private void methodView(JSONObject metadata) throws Exception {
        uploadBytes = null; uploadMethod = null; uploadButton = null; exportResponse = null;
        currentMethod = metadata.getString("name"); content.removeAllViews(); scroll.smoothScrollTo(0, 0);
        content.addView(button("← Wróć do katalogu", () -> { currentMethod = null; tab = 3; render(); }, false));
        title(currentMethod.substring("services/".length()), plain(metadata.optString("brief_description")));
        JSONObject auth = metadata.getJSONObject("auth_options");
        LinearLayout docs = card(content, Color.WHITE);
        add(docs, "Dostęp i uprawnienia", 17, INK, true);
        add(docs, "Klucz: " + auth.optString("consumer") + " · token: " + auth.optString("token")
            + "\nZakresy: " + auth.optJSONArray("scopes") + (auth.optBoolean("administrative_only") ? "\nWYMAGANY KLUCZ ADMINISTRACYJNY UCZELNI" : ""), 13, MUTED, false);
        if (metadata.optBoolean("beta")) note(docs, "Metoda beta — może zmienić zachowanie.", GREEN);
        if (!metadata.isNull("deprecated")) note(docs, "Metoda wycofywana: " + plain(metadata.opt("deprecated").toString()), Color.rgb(167, 99, 24));
        add(docs, plain(metadata.optString("description")), 14, INK, false);
        JSONArray arguments = metadata.getJSONArray("arguments");
        LinkedHashMap<String, EditText> inputs = new LinkedHashMap<>(); Set<String> required = new HashSet<>();
        LinearLayout params = card(content, Color.WHITE); add(params, "Parametry żądania", 18, INK, true);
        for (int i = 0; i < arguments.length(); i++) {
            JSONObject arg = arguments.getJSONObject(i); String key = arg.getString("name");
            if (Set.of("oauth_consumer_key", "oauth_nonce", "oauth_timestamp", "oauth_signature", "oauth_signature_method", "oauth_version", "oauth_token").contains(key)) continue;
            boolean needed = arg.optBoolean("is_required"); if (needed) required.add(key);
            String defaultValue = arg.isNull("default_value") ? "" : arg.optString("default_value");
            // Defaults described in prose are not literal parameters. Blank optionals are omitted.
            add(params, key + (needed ? " *" : "") + (defaultValue.isEmpty() ? "" : " · domyślnie: " + defaultValue), 13, INK, true);
            if (key.equals("data") && arg.optString("description").contains("multipart/form-data")) {
                uploadButton = button("Wybierz plik do wysłania (maks. 8 MB)", () -> {
                    uploadMethod = currentMethod;
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), 21);
                }, false);
                params.addView(uploadButton); add(params, plain(arg.optString("description")), 12, MUTED, false); continue;
            }
            EditText input = field(params, needed ? "Wymagany parametr" : "Puste = domyślna wartość serwera", "", key.toLowerCase(Locale.ROOT).contains("secret"));
            inputs.put(key, input); add(params, plain(arg.optString("description")), 12, MUTED, false);
        }
        add(params, "Transport", 13, INK, true);
        Spinner transport = new Spinner(this); transport.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"GET", "POST (formularz)"})); params.addView(transport);
        TextView result = text("Odpowiedź pojawi się po wykonaniu żądania.", 12, INK, false);
        result.setTextIsSelectable(true); result.setTypeface(Typeface.MONOSPACE);
        params.addView(button("Wykonaj żądanie API", () -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<String, EditText> input : inputs.entrySet()) {
                String value = input.getValue().getText().toString();
                if (value.trim().isEmpty()) { if (required.contains(input.getKey())) { error("Wypełnij parametr: " + input.getKey()); return; } }
                else values.put(input.getKey(), value);
            }
            if (uploadButton != null && uploadBytes == null) { error("Wybierz plik do wysłania."); return; }
            String method = transport.getSelectedItemPosition() == 0 ? "GET" : "POST";
            String endpoint = metadata.optString("name");
            new AlertDialog.Builder(this).setTitle("Wykonać żądanie " + method + "?")
                .setMessage(endpoint + "\n\nWybrane parametry zostaną przekazane Vistuli. Niektóre metody mogą zmieniać lub usuwać dane w USOS. Sprawdź opis tej metody przed wykonaniem.")
                .setNegativeButton("Anuluj", null).setPositiveButton("Wykonaj", (dialog, which) -> task(() -> {
                    JSONObject credentials = repository.read();
                    if (demo) credentials = new JSONObject();
                    return new UsosClient(credentials).explore(metadata, values, method, uploadBytes, "upload.bin");
                }, response -> {
                    exportResponse = response;
                    String formatted = response.binary() ? "Odpowiedź binarna: " + response.mime + " · " + response.bytes.length + " bajtów. Użyj „Zapisz odpowiedź”." : response.text();
                    try {
                        Object parsed = new JSONTokener(response.text()).nextValue();
                        if (parsed instanceof JSONObject) formatted = ((JSONObject) parsed).toString(2);
                        else if (parsed instanceof JSONArray) formatted = ((JSONArray) parsed).toString(2);
                    } catch (JSONException ignored) {}
                    // OAuth results are not exposed by the generic viewer.
                    if (endpoint.startsWith("services/oauth/") || endpoint.startsWith("services/oauth2/"))
                        { formatted = "Wykonano operację OAuth. Odpowiedź ukryta ze względu na możliwe dane uwierzytelniające. Do logowania użyj ekranu Konto."; exportResponse = null; }
                    if (formatted.length() > 100000) formatted = formatted.substring(0, 100000) + "\n[Podgląd ograniczony do 100 000 znaków]";
                    result.setText(formatted);
                })).show();
        }, true));
        LinearLayout output = card(content, Color.WHITE); add(output, "Odpowiedź API", 17, INK, true); output.addView(result);
        output.addView(button("Zapisz odpowiedź", () -> {
            if (exportResponse == null) { error("Najpierw wykonaj żądanie zwracające dane do zapisania."); return; }
            String extension = exportResponse.mime.contains("json") ? ".json" : exportResponse.mime.contains("pdf") ? ".pdf" : exportResponse.mime.contains("calendar") ? ".ics" : exportResponse.binary() ? ".bin" : ".txt";
            startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(exportResponse.mime).addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE, "usos-response" + extension), 22);
        }, false));
        LinearLayout returns = card(content, Color.WHITE); add(returns, "Struktura odpowiedzi", 17, INK, true);
        add(returns, plain(metadata.optString("returns")), 13, MUTED, false);
        JSONArray fields = metadata.optJSONArray("result_fields");
        if (fields != null && fields.length() > 0) {
            add(returns, "Pola odpowiedzi (parametr fields)", 16, INK, true);
            for (int i = 0; i < fields.length(); i++) {
                JSONObject f = fields.getJSONObject(i);
                add(returns, f.getString("name") + (f.optBoolean("is_secondary") ? " · zagnieżdżone" : ""), 13, INK, true);
                add(returns, plain(f.optString("description")), 12, MUTED, false);
            }
        }
        if (!metadata.optString("errors").isEmpty()) { add(returns, "Błędy", 16, INK, true); add(returns, plain(metadata.optString("errors")), 13, MUTED, false); }
        content.addView(button("Dokumentacja online", () -> browser(UsosClient.BASE + "developers/api/" + metadata.optString("name")
            .substring("services/".length(), metadata.optString("name").lastIndexOf('/')) + "/"), false));
        note(content, "GET, POST i wysyłanie plików blobbox przez multipart. Odpowiedzi tekstowe i pliki można zapisać przez systemowy wybór dokumentu. Limit transferu: 8 MB. Interaktywne metody OAuth obsługuje ekran Konto. Uprawnienia sprawdza USOS.", MUTED);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == 21 && currentMethod != null && currentMethod.equals(uploadMethod)) {
            task(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    if (input == null) throw new IOException();
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > 8 * 1024 * 1024) throw new IllegalArgumentException("Plik przekracza limit 8 MB.");
                        bytes.write(buffer, 0, count);
                    }
                    return bytes.toByteArray();
                }
            }, bytes -> { uploadBytes = bytes; uploadButton.setText(getString(R.string.selected_upload, bytes.length)); });
        } else if (requestCode == 22 && exportResponse != null) {
            byte[] bytes = exportResponse.bytes;
            task(() -> {
                try (OutputStream output = getContentResolver().openOutputStream(uri)) {
                    if (output == null) throw new IOException(); output.write(bytes);
                }
                return true;
            }, ok -> Toast.makeText(this, "Zapisano odpowiedź API", Toast.LENGTH_SHORT).show());
        }
    }
    @Override public void onBackPressed() {
        if (busy) return;
        if (currentMethod != null) { currentMethod = null; render(); }
        else if (tab != 0) { tab = 0; render(); }
        else super.onBackPressed();
    }
    private String asset(String path) throws IOException {
        try (InputStream input = getAssets().open(path); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return bytes.toString("UTF-8");
        }
    }
    private void requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    private void browser(String address) {
        Uri uri = Uri.parse(address);
        if (!"https".equals(uri.getScheme()) || !"usosapps.vistula.edu.pl".equals(uri.getHost())) { error("Adres spoza USOS Vistuli."); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException e) { error("Zainstaluj przeglądarkę, aby otworzyć USOS."); }
    }
    private void error(String message) { new AlertDialog.Builder(this).setTitle("Nie udało się wykonać operacji").setMessage(message).setPositiveButton("OK", null).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setLineSpacing(dp(3), 1f); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t;
    }
    private void add(LinearLayout target, String value, int size, int color, boolean bold) {
        TextView t = text(value, size, color, bold); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); target.addView(t, p);
    }
    private LinearLayout card(LinearLayout parent, int color) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(20), dp(20), dp(12));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(18)); box.setBackground(shape);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); parent.addView(box, p); return box;
    }
    private Button button(String label, Runnable action, boolean primary) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(primary ? Color.WHITE : INK);
        b.setPadding(dp(12), dp(8), dp(12), dp(8)); b.setMinHeight(dp(48));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(primary ? GREEN : Color.rgb(232, 238, 242)); shape.setCornerRadius(dp(12)); b.setBackground(shape);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(8); b.setLayoutParams(p); b.setOnClickListener(v -> { if (!busy) action.run(); }); return b;
    }
    private EditText field(LinearLayout parent, String hint, String value, boolean password) {
        EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setTextSize(14); e.setTextColor(INK);
        e.setSingleLine(false); e.setPadding(dp(8), dp(12), dp(8), dp(12)); e.setSaveEnabled(false);
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | (password ? android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD : android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        if (password) e.setTransformationMethod(PasswordTransformationMethod.getInstance());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(12); parent.addView(e, p); return e;
    }
    private void heading(String value) { add(content, value, 20, INK, true); }
    private void title(String value, String description) { add(content, value, 28, INK, true); add(content, description, 14, MUTED, false); }
    private void empty(String title, String description) { LinearLayout box = card(content, Color.WHITE); add(box, title, 17, INK, true); add(box, description, 14, MUTED, false); }
    private void note(LinearLayout parent, String value, int color) { add(parent, value, 12, color, false); }
    private interface Choice { void choose(String value); }
    private void chips(String[] values, String[] labels, String selected, Choice action) {
        LinearLayout line = new LinearLayout(this);
        for (int i = 0; i < values.length; i++) {
            final String value = values[i]; Button b = button(labels[i], () -> action.choose(value), value.equals(selected));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1); p.rightMargin = dp(6); line.addView(b, p);
        }
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(18); content.addView(line, p);
    }
    private String timestamp(long millis) {
        return millis == 0 ? "Jeszcze nie sprawdzano" : Instant.ofEpochMilli(millis).atZone(UsosClient.ZONE)
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm:ss", Locale.forLanguageTag("pl-PL")));
    }
    private String plain(String html) { return Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString().trim(); }
}
