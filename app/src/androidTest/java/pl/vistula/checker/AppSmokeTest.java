package pl.vistula.checker;

import android.content.Context;
import android.app.Activity;
import android.widget.*;
import android.view.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AppSmokeTest {
    @Rule public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(MainActivity.class);
    private Activity activity() { return rule.getActivity(); }
    private View find(View root, String text, boolean button) {
        if (root instanceof TextView && (!button || root instanceof Button) && ((TextView) root).getText().toString().equals(text)) return root;
        if (root instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) root).getChildCount(); i++) {
            View result = find(((ViewGroup) root).getChildAt(i), text, button); if (result != null) return result;
        }
        return null;
    }
    private void click(String text) throws Exception {
        long until = System.currentTimeMillis() + 10000;
        boolean[] done = {false};
        while (!done[0] && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                View v = find(activity().getWindow().getDecorView(), text, true);
                if (v != null && v.isEnabled()) { v.performClick(); done[0] = true; }
            });
            if (!done[0]) Thread.sleep(100);
        }
        assertTrue("Button not available: " + text, done[0]);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
    private void has(String text) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
            assertNotNull("Missing text: " + text, find(activity().getWindow().getDecorView(), text, false)));
    }
    @Test public void demoSimulatesChangesAndRemainsSeparateFromLiveAccount() throws Exception {
        click("Zobacz wersję demonstracyjną"); click("Symuluj zmiany");
        has("Historia zmian"); has("Podstawy finansów"); has("Statystyka opisowa");
        click("Dane"); has("Podstawy finansów");
        click("Plan · 28 dni"); has("Statystyka opisowa");
        click("Konto"); click("Zakończ tryb demonstracyjny");
        has("Połącz z USOS"); assertTrue(new Repository(activity()).read().optString("token").isEmpty());
    }
    @Test public void everyCatalogEntryHasMatchingOfflineMetadata() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONArray index = new JSONArray(read(c, "api/index.json"));
        assertEquals(571, index.length());
        for (int i = 0; i < index.length(); i++) {
            String name = index.getJSONObject(i).getString("name");
            JSONObject meta = new JSONObject(read(c, "api/" + name.replace("/", "__") + ".json"));
            assertEquals(name, meta.getString("name")); assertNotNull(meta.getJSONArray("arguments")); assertNotNull(meta.getJSONObject("auth_options"));
        }
    }
    @Test public void apiSearchOpensOfflineMethodAndRequiredParameters() throws Exception {
        click("API");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            ViewGroup root = (ViewGroup) activity().getWindow().getDecorView();
            EditText input = firstInput(root); assertNotNull(input); input.setText("grades/latest");
        });
        click("grades/latest\nGet user's latest grades");
        long until = System.currentTimeMillis() + 5000; boolean[] found = {false};
        while (!found[0] && System.currentTimeMillis() < until) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> found[0] = find(activity().getWindow().getDecorView(), "Parametry żądania", false) != null);
            if (!found[0]) Thread.sleep(100);
        }
        assertTrue(found[0]); has("Wykonaj żądanie API"); has("Dokumentacja online");
    }
    private EditText firstInput(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View v = root.getChildAt(i); if (v instanceof EditText) return (EditText) v;
            if (v instanceof ViewGroup) { EditText result = firstInput((ViewGroup) v); if (result != null) return result; }
        }
        return null;
    }
    @Test public void encryptedVaultRoundTripsAndDoesNotWriteTokensAsPlaintext() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Repository r = new Repository(c); String marker = "sensitive_token_test_6d8b1c";
        r.update(s -> s.put("testMarker", marker)); assertEquals(marker, r.read().getString("testMarker"));
        byte[] bytes = java.nio.file.Files.readAllBytes(new java.io.File(c.getNoBackupFilesDir(), "state.bin").toPath());
        assertFalse(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1).contains(marker));
        r.update(s -> s.remove("testMarker"));
    }
    @Test public void failedSyncPreservesSnapshotAndHistoryAndRetryDoesNotDuplicateChanges() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Repository r = new Repository(c);
        String day = java.time.LocalDate.now(UsosClient.ZONE).toString();
        pl.vistula.checker.core.Snapshot initial = new pl.vistula.checker.core.Snapshot(day, java.time.LocalDate.now(UsosClient.ZONE).plusDays(28).toString());
        pl.vistula.checker.core.Snapshot updated = new pl.vistula.checker.core.Snapshot(initial.start, initial.end);
        initial.add(new pl.vistula.checker.core.Record("grade:test", "ocena", "Test", "4", day + " 10:00:00", ""));
        updated.add(new pl.vistula.checker.core.Record("grade:test", "ocena", "Test", "5", day + " 10:30:00", ""));
        try {
            r.update(s -> s.put("token", "test-only-token").put("enabled", true));
            assertTrue(r.sync(true, s -> initial).isEmpty());
            assertEquals(1, r.sync(true, s -> updated).size());
            JSONObject before = r.read();
            try { r.sync(true, s -> { throw new java.io.IOException("simulated unavailable source"); }); fail("Expected failure"); }
            catch (java.io.IOException expected) {}
            JSONObject after = r.read();
            assertEquals(before.getJSONObject("snapshot").toString(), after.getJSONObject("snapshot").toString());
            assertEquals(before.getJSONArray("history").toString(), after.getJSONArray("history").toString());
            assertEquals(before.getLong("successAt"), after.getLong("successAt"));
            assertFalse(after.optString("error").isEmpty());
            assertTrue(r.sync(true, s -> updated).isEmpty()); assertEquals(1, r.read().getJSONArray("history").length());
        } finally { r.disconnect(); }
    }
    @Test public void publicApiInstallationWorksWithoutCredentials() throws Exception {
        JSONObject method = new JSONObject("{\"name\":\"services/apisrv/installation\",\"auth_options\":{\"consumer\":\"ignored\",\"token\":\"ignored\",\"administrative_only\":false}}");
        UsosClient.Response response = new UsosClient(new JSONObject()).explore(method, java.util.Map.of(), "GET", null, null);
        JSONObject result = new JSONObject(response.text());
        assertEquals(UsosClient.BASE, result.getString("base_url"));
        assertTrue(result.getJSONObject("institution_name").getString("pl").contains("Vistula"));
    }
    private String read(Context c, String path) throws Exception {
        try (java.io.InputStream input = c.getAssets().open(path); java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int n; while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n); return output.toString("UTF-8");
        }
    }
}
