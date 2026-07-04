package com.fongmi.android.tv.api;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;

import java.util.HashSet;
import java.util.Set;

public final class CspWarmup {

    private static final long DELAY_MS = 500;
    private static final Object LOCK = new Object();

    private static final Set<String> attemptedKeys = new HashSet<>();

    private CspWarmup() {
    }

    public static void schedule(String reason) {
        if (!Setting.isCspWarmup()) return;
        String key = configKey();
        synchronized (LOCK) {
            if (!attemptedKeys.add(key)) {
                SpiderDebug.log("csp-warmup", "skip duplicate reason=%s config=%s", reason, key);
                return;
            }
        }
        SpiderDebug.log("csp-warmup", "schedule reason=%s config=%s delay=%sms", reason, key, DELAY_MS);
        App.post(() -> Task.execute(() -> run(key, reason)), DELAY_MS);
    }

    private static void run(String key, String reason) {
        long start = System.currentTimeMillis();
        try {
            if (!Setting.isCspWarmup()) {
                SpiderDebug.log("csp-warmup", "cancel disabled reason=%s", reason);
                return;
            }
            Site site = pickSite();
            if (site == null) {
                SpiderDebug.log("csp-warmup", "skip no native csp reason=%s cost=%sms", reason, System.currentTimeMillis() - start);
                return;
            }
            SpiderDebug.log("csp-warmup", "init start reason=%s site=%s api=%s", reason, site.getKey(), site.getApi());
            site.recent().spider();
            SpiderDebug.log("csp-warmup", "done reason=%s site=%s api=%s cost=%sms", reason, site.getKey(), site.getApi(), System.currentTimeMillis() - start);
        } catch (Throwable e) {
            SpiderDebug.log("csp-warmup", "error reason=%s err=%s msg=%s cost=%sms", reason, e.getClass().getSimpleName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private static Site pickSite() {
        Site fallback = null;
        for (Site site : VodConfig.get().getSites()) {
            if (!isWarmable(site)) continue;
            if (fallback == null) fallback = site;
            if (!site.hasHomePage()) return site;
        }
        return fallback;
    }

    private static boolean isWarmable(Site site) {
        if (site == null || site.isEmpty() || site.getType() != 3) return false;
        String api = site.getApi();
        return !TextUtils.isEmpty(api) && api.startsWith("csp_") && !"csp_Builtin".equalsIgnoreCase(api);
    }

    private static String configKey() {
        return VodConfig.getCid() + ":" + VodConfig.getUrl();
    }
}
