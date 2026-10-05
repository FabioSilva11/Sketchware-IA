package pro.sketchware.chat;

import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts provider API keys (and other secrets) before they touch disk.
 *
 * Format persisted in SharedPreferences: "enc:v1:" + Base64(iv) + ":" + Base64(ciphertext)
 * Key material lives in the Android Keystore ( StrongBox when available) and never
 * leaves secure hardware, so a leaked preferences file is not enough to recover keys.
 *
 * Fail-open policy: values that cannot be decrypted are returned as-is so a
 * Keystore invalidation (e.g. after clearing lock-screen credentials) degrades to
 * a re-entry prompt in the provider settings instead of crashing the app.
 */
public final class SecretStore {
    private static final String TAG = "SecretStore";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "axion_secret_key_v1";
    private static final String PREFIX = "enc:v1:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private SecretStore() {
    }

    /** True when the value looks like it was produced by {@link #encrypt}. */
    public static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Encrypts a secret. Plain input is returned unchanged when Keystore is
     * unavailable so callers never lose data (degrades to plaintext on disk).
     */
    public static String encrypt(String plain) {
        if (plain == null || plain.isEmpty() || isEncrypted(plain)) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, obtainKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(ciphertext, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.w(TAG, "encrypt failed; storing as-is", e);
            return plain;
        }
    }

    /** Decrypts a value produced by {@link #encrypt}; other inputs pass through. */
    public static String decrypt(String value) {
        if (!isEncrypted(value)) {
            return value;
        }
        try {
            String[] parts = value.substring(PREFIX.length()).split(":", 2);
            if (parts.length != 2) {
                return value;
            }
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, obtainKey(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.w(TAG, "decrypt failed; treating value as unusable", e);
            return "";
        }
    }

    private static SecretKey obtainKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry entry = keyStore.getEntry(KEY_ALIAS, null);
        if (entry instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            spec.setUnlockedDeviceRequired(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Prefer the strongest storage when the device offers it.
            try {
                spec.setIsStrongBoxBacked(true);
            } catch (Exception ignored) {
                // StrongBox unavailable on this device; default backing is fine.
            }
        }
        generator.init(spec.build());
        return generator.generateKey();
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 128-bit random token, e.g. for non-secret identifiers. */
    public static String randomToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return Base64.encodeToString(bytes, Base64.NO_WRAP | Base64.URL_SAFE);
    }
}
