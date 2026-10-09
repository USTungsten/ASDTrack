package com.ellesjourney.tracker;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

final class CryptoBox {
    private static final int ITERATIONS = 150_000;
    private static final byte[] AAD = "EllesJourney-v1".getBytes(StandardCharsets.UTF_8);

    private CryptoBox() {}

    static String encrypt(String plaintext, String passphrase) throws Exception {
        byte[] salt = new byte[16];
        byte[] iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        SecretKey key = derive(passphrase, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return "EJ1." + Base64.encodeToString(salt, Base64.NO_WRAP) + "."
                + Base64.encodeToString(iv, Base64.NO_WRAP) + "."
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }

    static String decrypt(String payload, String passphrase) throws Exception {
        String[] parts = payload.trim().split("\\.", 4);
        if (parts.length != 4 || !"EJ1".equals(parts[0])) throw new Exception("Unsupported encrypted data format");
        byte[] salt = Base64.decode(parts[1], Base64.NO_WRAP);
        byte[] iv = Base64.decode(parts[2], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[3], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, derive(passphrase, salt), new GCMParameterSpec(128, iv));
        cipher.updateAAD(AAD);
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey derive(String passphrase, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256);
        try {
            byte[] bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(bytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }
}
