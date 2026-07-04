package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RemoteSyncConfig {

    public String id;
    public String name;
    public boolean enabled;
    public String url;
    public String token;
    public List<String> siteKeys;
    public boolean syncOnStartup;
    public int intervalMinutes;
    public int maxItems;
    public long lastSyncAt;
    public long lastSuccessAt;
    public int lastFetched;
    public int lastApplied;
    public int lastSkipped;
    public int lastFailed;
    public String lastError;

    public RemoteSyncConfig() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.enabled = true;
        this.url = "";
        this.token = "";
        this.siteKeys = new ArrayList<>();
        this.syncOnStartup = true;
        this.intervalMinutes = 0;
        this.maxItems = 100;
        this.lastError = "";
    }

    public boolean isUsable() {
        return enabled && !TextUtils.isEmpty(url) && url.startsWith("http");
    }

    public boolean matchesSite(String siteKey) {
        if (siteKeys == null || siteKeys.isEmpty()) return true;
        for (String item : siteKeys) if (TextUtils.equals(normalize(item), normalize(siteKey))) return true;
        return false;
    }

    public boolean shouldSyncNow(long now, boolean startup) {
        if (!isUsable()) return false;
        if (startup && syncOnStartup) return true;
        if (intervalMinutes <= 0) return false;
        return now - lastSyncAt >= intervalMinutes * 60_000L;
    }

    public String displayName() {
        if (!TextUtils.isEmpty(name)) return name;
        String host = host(url);
        if (!TextUtils.isEmpty(host)) return host;
        if (!TextUtils.isEmpty(url)) return url;
        return "Remote sync";
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String host(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (Exception e) {
            return "";
        }
    }
}
