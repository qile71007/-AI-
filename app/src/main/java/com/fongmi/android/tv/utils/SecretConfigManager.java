package com.fongmi.android.tv.utils;

public class SecretConfigManager {
    private static SecretConfigManager instance;
    private boolean unlocked = false;
    private final String[] unlockKeys = {"七乐私密2026", "private7l"};

    private SecretConfigManager() {}

    public static SecretConfigManager getInstance() {
        if (instance == null) {
            instance = new SecretConfigManager();
        }
        return instance;
    }

    public boolean checkUnlockKey(String input) {
        if (input == null) return false;
        String content = input.trim();
        for (String key : unlockKeys) {
            if (key.equals(content)) {
                return true;
            }
        }
        return false;
    }

    public void unlock() {
        unlocked = true;
    }

    public void lock() {
        unlocked = false;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public void reset() {
        unlocked = false;
    }
}