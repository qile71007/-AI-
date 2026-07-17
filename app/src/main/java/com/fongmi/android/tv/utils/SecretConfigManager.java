package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.Config;

import java.util.ArrayList;
import java.util.List;

public class SecretConfigManager {
    private static volatile SecretConfigManager instance;
    private boolean unlocked = false;
    private String currentKeyword = null;

    private SecretConfigManager() {}

    public static SecretConfigManager getInstance() {
        if (instance == null) {
            synchronized (SecretConfigManager.class) {
                if (instance == null) {
                    instance = new SecretConfigManager();
                }
            }
        }
        return instance;
    }

    public boolean unlock(String keyword) {
        if (TextUtils.isEmpty(keyword)) return false;
        String kw = keyword.trim();
        List<Config> allConfigs = new ArrayList<>();
        List<Config> vodConfigs = Config.getAll(0);
        List<Config> liveConfigs = Config.getAll(1);
        if (vodConfigs != null) allConfigs.addAll(vodConfigs);
        if (liveConfigs != null) allConfigs.addAll(liveConfigs);
        for (Config config : allConfigs) {
            if (config.isSecret() && kw.equals(config.getUnlockKeyword())) {
                this.unlocked = true;
                this.currentKeyword = kw;
                return true;
            }
        }
        return false;
    }

    public void lock() {
        this.unlocked = false;
        this.currentKeyword = null;
    }

    public boolean isUnlocked() {
        return unlocked;
    }

    public String getCurrentKeyword() {
        return currentKeyword;
    }

    public void reset() {
        lock();
    }
}
