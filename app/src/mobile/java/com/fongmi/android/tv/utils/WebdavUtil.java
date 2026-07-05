package com.fongmi.android.tv.utils;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class WebdavUtil {

    private static final String TAG = "WebdavUtil";
    private static final String BACKUP_DIR = "/webhtv-backup/";

    public static class RemoteFile {
        public String path;
        public long modified;
        public long size;

        public RemoteFile(String path, long modified, long size) {
            this.path = path;
            this.modified = modified;
            this.size = size;
        }
    }

    public static class ListResult {
        public String rawXml;
        public List<RemoteFile> files;
    }

    private static OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private static Request.Builder getAuthRequestBuilder(String url, String user, String pass) {
        Request.Builder builder = new Request.Builder().url(url);
        if (user != null && !user.isEmpty() && pass != null) {
            builder.header("Authorization", Credentials.basic(user, pass));
        }
        return builder;
    }

    // 改进的测试连接方法 - 直接对根路径发送 PROPFIND
    public static boolean testConnection(String url, String user, String pass) {
        try {
            // 去除末尾多余的斜杠
            String baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
            Request request = getAuthRequestBuilder(baseUrl, user, pass)
                    .method("PROPFIND", null)
                    .header("Depth", "0")
                    .build();
            Response response = getClient().newCall(request).execute();
            int code = response.code();
            response.close();
            // 2xx 或 207 表示成功
            return code >= 200 && code < 300 || code == 207;
        } catch (Exception e) {
            Log.e(TAG, "testConnection error", e);
            return false;
        }
    }

    // 上传文件
    public static boolean uploadFile(String url, String user, String pass, File localFile) {
        if (localFile == null || !localFile.exists()) return false;
        try {
            String base = url.endsWith("/") ? url : url + "/";
            String dirUrl = base + "webhtv-backup/";
            // 先尝试创建目录（使用MKCOL）
            try {
                Request mkcolRequest = getAuthRequestBuilder(dirUrl, user, pass)
                        .method("MKCOL", null)
                        .build();
                Response mkcolResponse = getClient().newCall(mkcolRequest).execute();
                mkcolResponse.close();
                // 忽略冲突（已存在）
            } catch (Exception ignored) {}

            String fileName = localFile.getName();
            String uploadUrl = dirUrl + fileName;
            Request request = getAuthRequestBuilder(uploadUrl, user, pass)
                    .put(okhttp3.RequestBody.create(localFile, okhttp3.MediaType.parse("application/octet-stream")))
                    .build();
            Response response = getClient().newCall(request).execute();
            boolean ok = response.isSuccessful();
            response.close();
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "uploadFile error", e);
            return false;
        }
    }

    // 下载文件
    public static boolean downloadFile(String url, String user, String pass, String remotePath, File destFile) {
        if (destFile == null) return false;
        try {
            String base = url.endsWith("/") ? url : url + "/";
            String downloadUrl = base + remotePath.replaceAll("^/+", "");
            Request request = getAuthRequestBuilder(downloadUrl, user, pass)
                    .get()
                    .build();
            Response response = getClient().newCall(request).execute();
            if (!response.isSuccessful()) {
                response.close();
                return false;
            }
            ResponseBody body = response.body();
            if (body == null) {
                response.close();
                return false;
            }
            InputStream is = body.byteStream();
            FileOutputStream fos = new FileOutputStream(destFile);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
            fos.close();
            is.close();
            response.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "downloadFile error", e);
            return false;
        }
    }

    // 获取文件列表
    public static List<RemoteFile> listFiles(String url, String user, String pass) {
        try {
            String base = url.endsWith("/") ? url : url + "/";
            String listUrl = base + "webhtv-backup/";
            Request request = getAuthRequestBuilder(listUrl, user, pass)
                    .method("PROPFIND", okhttp3.RequestBody.create(
                            "<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind xmlns=\"DAV:\"><prop><getlastmodified/><getcontentlength/><resourcetype/></prop></propfind>",
                            okhttp3.MediaType.parse("application/xml")))
                    .header("Depth", "1")
                    .build();
            Response response = getClient().newCall(request).execute();
            if (!response.isSuccessful()) {
                response.close();
                return null;
            }
            String xml = response.body().string();
            response.close();
            return parsePropfindResponse(xml);
        } catch (Exception e) {
            Log.e(TAG, "listFiles error", e);
            return null;
        }
    }

    // 解析PROPFIND响应
    private static List<RemoteFile> parsePropfindResponse(String xml) {
        List<RemoteFile> files = new ArrayList<>();
        String[] responses = xml.split("<response>");
        for (int i = 1; i < responses.length; i++) {
            String resp = responses[i];
            String href = extractTag(resp, "href");
            if (href == null) continue;
            href = href.trim();
            if (href.endsWith("/")) continue;
            String lastModified = extractTag(resp, "getlastmodified");
            long time = 0;
            if (lastModified != null) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                    time = sdf.parse(lastModified.trim()).getTime();
                } catch (Exception e) {
                    time = System.currentTimeMillis();
                }
            }
            String sizeStr = extractTag(resp, "getcontentlength");
            long size = 0;
            if (sizeStr != null) {
                try {
                    size = Long.parseLong(sizeStr.trim());
                } catch (NumberFormatException ignored) {}
            }
            String fileName = href.substring(href.lastIndexOf('/') + 1);
            if (fileName.endsWith(".bk.gz") || fileName.endsWith(".zip")) {
                files.add(new RemoteFile(href, time, size));
            }
        }
        return files;
    }

    private static String extractTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        if (start == -1) return null;
        int end = xml.indexOf(close, start);
        if (end == -1) return null;
        return xml.substring(start + open.length(), end);
    }
}
