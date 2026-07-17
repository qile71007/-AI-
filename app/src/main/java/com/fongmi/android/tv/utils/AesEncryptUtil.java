package com.fongmi.android.tv.utils;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesEncryptUtil {
    private static final String KEY = "qile7l2026secret";
    private static final String IV = "7lprivate1234567";   // 改为16位
    private static final String MODE = "AES/CBC/PKCS5Padding";

    public static String encrypt(String text) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes());
        Cipher cipher = Cipher.getInstance(MODE);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] result = cipher.doFinal(text.getBytes("UTF-8"));
        return Base64.encodeToString(result, Base64.NO_WRAP);
    }

    public static String decrypt(String text) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(), "AES");
        IvParameterSpec ivSpec = new IvParameterSpec(IV.getBytes());
        Cipher cipher = Cipher.getInstance(MODE);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
        byte[] decodeByte = Base64.decode(text, Base64.NO_WRAP);
        byte[] result = cipher.doFinal(decodeByte);
        return new String(result, "UTF-8");
    }
}
