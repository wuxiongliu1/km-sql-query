package com.kisf.sqlquery.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class AesUtils {

    private static final String ALGORITHM = "AES";
    private static final byte[] DEFAULT_KEY = "KmSqlQuery@2026!".getBytes();
    private static final String KEY_PROPERTY = "km.sql-query.aes-key";
    private static final String KEY_ENVIRONMENT = "KM_SQL_QUERY_AES_KEY";

    public static String encrypt(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(resolveKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES encrypt failed", e);
        }
    }

    public static String decrypt(String encryptedText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(resolveKey(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            return new String(cipher.doFinal(decoded), "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("AES decrypt failed", e);
        }
    }

    private static byte[] resolveKey() {
        String configuredKey = System.getProperty(KEY_PROPERTY);
        if (configuredKey == null || configuredKey.isEmpty()) {
            configuredKey = System.getenv(KEY_ENVIRONMENT);
        }
        byte[] key = configuredKey == null || configuredKey.isEmpty()
                ? DEFAULT_KEY
                : configuredKey.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalArgumentException(
                    "AES key must contain 16, 24, or 32 UTF-8 bytes");
        }
        return key;
    }
}
