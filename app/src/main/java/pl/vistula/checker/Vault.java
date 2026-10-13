package pl.vistula.checker;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Tokens, snapshots and history are encrypted with a non-exportable Android Keystore key. */
final class Vault {
    private static final String ALIAS = "vistula_checker_local_v1";
    private final AtomicFile file;
    Vault(Context context) { file = new AtomicFile(new File(context.getNoBackupFilesDir(), "state.bin")); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    JSONObject read() throws Exception {
        if (!file.getBaseFile().exists()) return new JSONObject();
        byte[] bytes = file.readFully();
        if (bytes.length < 29 || bytes[0] != 1) throw new IOException("Nie można odczytać zapisanych danych.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 1, 12));
        return new JSONObject(new String(cipher.doFinal(bytes, 13, bytes.length - 13), StandardCharsets.UTF_8));
    }
    void write(JSONObject state) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(state.toString().getBytes(StandardCharsets.UTF_8));
        FileOutputStream stream = file.startWrite();
        try {
            stream.write(1); stream.write(cipher.getIV()); stream.write(encrypted);
            file.finishWrite(stream);
        } catch (Exception e) { file.failWrite(stream); throw e; }
    }
    void clear() { file.delete(); }
}
