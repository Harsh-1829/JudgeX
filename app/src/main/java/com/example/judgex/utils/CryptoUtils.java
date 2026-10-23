package com.example.judgex.utils;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class CryptoUtils {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // in bits
    private static final int IV_LENGTH = 12; // 12 bytes standard for GCM

    // 256-bit AES master key used for internal credential protection
    private static final byte[] MASTER_KEY_BYTES = {
            (byte) 0x4a, (byte) 0x75, (byte) 0x64, (byte) 0x67, // 'J','u','d','g'
            (byte) 0x65, (byte) 0x58, (byte) 0x5f, (byte) 0x53, // 'e','X','_','S'
            (byte) 0x65, (byte) 0x63, (byte) 0x75, (byte) 0x72, // 'e','c','u','r'
            (byte) 0x65, (byte) 0x5f, (byte) 0x4b, (byte) 0x65, // 'e','_','K','e'
            (byte) 0x79, (byte) 0x5f, (byte) 0x32, (byte) 0x30, // 'y','_','2','0'
            (byte) 0x32, (byte) 0x36, (byte) 0x5f, (byte) 0x41, // '2','6','_','A'
            (byte) 0x64, (byte) 0x6d, (byte) 0x69, (byte) 0x6e, // 'd','m','i','n'
            (byte) 0x5f, (byte) 0x54, (byte) 0x65, (byte) 0x61  // '_','T','e','a'
    };

    private CryptoUtils() {
        // Utility class
    }

    private static SecretKey getSecretKey() {
        return new SecretKeySpec(MASTER_KEY_BYTES, "AES");
    }

    /**
     * Encrypts plaintext using AES-GCM-256 with a randomized 12-byte IV.
     * Returns Base64-encoded [IV + CipherText + Tag].
     * Never logs or outputs plaintext.
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), spec);

            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

            return encodeBase64(combined);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Decrypts Base64-encoded [IV + CipherText + Tag] using AES-GCM-256.
     * Returns original plaintext string, or empty string on failure.
     */
    public static String decrypt(String encryptedBase64) {
        if (encryptedBase64 == null || encryptedBase64.trim().isEmpty()) {
            return "";
        }
        try {
            byte[] combined = decodeBase64(encryptedBase64.trim());
            if (combined.length <= IV_LENGTH) {
                return "";
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

            byte[] plainBytes = cipher.doFinal(cipherText);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String encodeBase64(byte[] data) {
        try {
            return java.util.Base64.getEncoder().encodeToString(data);
        } catch (Throwable ignored) {
            return Base64.encodeToString(data, Base64.NO_WRAP);
        }
    }

    private static byte[] decodeBase64(String base64) {
        try {
            return java.util.Base64.getDecoder().decode(base64);
        } catch (Throwable ignored) {
            return Base64.decode(base64, Base64.NO_WRAP);
        }
    }
}
