package com.fongmi.android.tv.utils;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistorySyncUtil {

    private static final String REMOTE_FILE = "/webhtv-backup/history_sync.json";
    private static final String LOCAL_FILE = "history_sync.json";

    public interface Callback {
        void onSuccess(String msg);
        void onError(String msg);
    }

    public static void upload(Context context, Callback callback) {
        new Thread(() -> {
            try {
                String url = Setting.getWebdavUrl();
                String user = Setting.getWebdavUser();
                String pass = Setting.getWebdavPass();
                if (url.isEmpty()) {
                    postError(callback, "请先配置 WebDAV");
                    return;
                }

                List<History> all = History.getAll();
                Gson gson = new Gson();
                Map<String, Object> data = new HashMap<>();
                data.put("version", 1);
                data.put("timestamp", System.currentTimeMillis());
                data.put("histories", all);
                String json = gson.toJson(data);

                File localFile = new File(Path.cache(), LOCAL_FILE);
                try (FileOutputStream fos = new FileOutputStream(localFile)) {
                    fos.write(json.getBytes("UTF-8"));
                }

                boolean ok = uploadWebdav(url, user, pass, localFile);
                if (ok) postSuccess(callback, "已上传 " + all.size() + " 条观看记录");
                else postError(callback, "上传失败，请检查 WebDAV 配置");
            } catch (Exception e) {
                postError(callback, "上传失败: " + e.getMessage());
            }
        }).start();
    }

    public static void download(Context context, Callback callback) {
        new Thread(() -> {
            try {
                String url = Setting.getWebdavUrl();
                String user = Setting.getWebdavUser();
                String pass = Setting.getWebdavPass();
                if (url.isEmpty()) {
                    postError(callback, "请先配置 WebDAV");
                    return;
                }

                File dest = new File(Path.cache(), LOCAL_FILE);
                boolean ok = downloadWebdav(url, user, pass, REMOTE_FILE, dest);
                if (!ok || !dest.exists()) {
                    postError(callback, "下载失败，可能还没有云端记录");
                    return;
                }

                String json = readFile(dest);
                if (json == null || json.isEmpty()) {
                    postError(callback, "云端记录为空");
                    return;
                }

                Gson gson = new Gson();
                Map<String, Object> data = gson.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                if (data == null || !data.containsKey("histories")) {
                    postError(callback, "云端数据格式错误");
                    return;
                }

                List<History> remote = gson.fromJson(gson.toJson(data.get("histories")), new TypeToken<List<History>>(){}.getType());
                if (remote == null || remote.isEmpty()) {
                    postError(callback, "云端无观看记录");
                    return;
                }

                List<History> local = History.getAll();
                Map<String, History> merged = new HashMap<>();
                for (History h : local) merged.put(h.getKey(), h);
                int newCount = 0;
                for (History h : remote) {
                    if (!merged.containsKey(h.getKey())) {
                        merged.put(h.getKey(), h);
                        newCount++;
                    } else {
                        History existing = merged.get(h.getKey());
                        if (h.getPosition() > existing.getPosition()) {
                            merged.put(h.getKey(), h);
                            newCount++;
                        }
                    }
                }

                for (History h : merged.values()) {
                    try { h.save(); } catch (Exception ignored) {}
                }

                postSuccess(callback, "已同步 " + newCount + " 条新记录（共 " + merged.size() + " 条）");
            } catch (Exception e) {
                postError(callback, "同步失败: " + e.getMessage());
            }
        }).start();
    }

    private static boolean uploadWebdav(String url, String user, String pass, File file) {
        try {
            okhttp3.RequestBody body = okhttp3.RequestBody.create(file, okhttp3.MediaType.parse("application/json"));
            String credentials = android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url + "/" + REMOTE_FILE)
                    .put(body)
                    .header("Authorization", "Basic " + credentials)
                    .build();
            try (okhttp3.Response resp = com.github.catvod.net.OkHttp.client().newCall(request).execute()) {
                return resp.isSuccessful();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean downloadWebdav(String url, String user, String pass, String remotePath, File dest) {
        try {
            String credentials = android.util.Base64.encodeToString((user + ":" + pass).getBytes(), android.util.Base64.NO_WRAP);
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url + "/" + remotePath)
                    .get()
                    .header("Authorization", "Basic " + credentials)
                    .build();
            try (okhttp3.Response resp = com.github.catvod.net.OkHttp.client().newCall(request).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return false;
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                    fos.write(resp.body().bytes());
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void postSuccess(Callback callback, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Notify.show(msg);
            if (callback != null) callback.onSuccess(msg);
        });
    }

    private static void postError(Callback callback, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Notify.show(msg);
            if (callback != null) callback.onError(msg);
        });
    }

    private static String readFile(File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
