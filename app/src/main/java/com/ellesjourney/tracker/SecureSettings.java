package com.ellesjourney.tracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSettings {
    private static final String PREFS = "github_sync_settings";
    private static final String KEY_ALIAS = "elles_journey_sync_key";
    private final SharedPreferences preferences;

    SecureSettings(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Config get() {
        Config config = new Config();
        config.owner = preferences.getString("owner", "USTungsten");
        config.repo = preferences.getString("repo", "ASDTrack");
        config.branch = preferences.getString("branch", "main");
        config.path = preferences.getString("path", "private-data/elle-tracker.enc");
        config.token = decrypt(preferences.getString("token", ""));
        config.passphrase = decrypt(preferences.getString("passphrase", ""));
        return config;
    }

    void save(Config config) throws Exception {
        preferences.edit()
                .putString("owner", config.owner)
                .putString("repo", config.repo)
                .putString("branch", config.branch)
                .putString("path", config.path)
                .putString("token", encrypt(config.token))
                .putString("passphrase", encrypt(config.passphrase))
                .apply();
    }

    private String encrypt(String value) throws Exception {
        if (value == null || value.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "."
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    private String decrypt(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            String[] parts = value.split("\\.", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    static final class Config {
        String owner;
        String repo;
        String branch;
        String path;
        String token;
        String passphrase;

        boolean isReady() {
            return !owner.isEmpty() && !repo.isEmpty() && !branch.isEmpty() && !path.isEmpty()
                    && !token.isEmpty() && passphrase.length() >= 12;
        }
    }
}
