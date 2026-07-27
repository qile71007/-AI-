package com.fongmi.android.tv.utils;

import android.text.TextUtils;
import com.github.catvod.net.OkHttp;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebdavUtil {
    public static class RemoteFile { public String path; public long modified; }

    private static final okhttp3.OkHttpClient client = com.github.catvod.net.OkHttp.client(15000);

    public static boolean testConnection(String url, String user, String pass) {
        try {
            Request.Builder builder = new Request.Builder().url(url).method("PROPFIND", RequestBody.create(MediaType.parse("application/xml"), "<?xml version='1.0'?><d:propfind xmlns:d='DAV:'><d:prop><d:displayname/></d:prop></d:propfind>"));
            if (!TextUtils.isEmpty(user)) builder.header("Authorization", okhttp3.Credentials.basic(user, pass != null ? pass : ""));
            try (Response resp = client.newCall(builder.build()).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { return false; }
    }

    public static boolean uploadFile(String url, String user, String pass, File file) {
        try {
            String remotePath = url.endsWith("/") ? url + file.getName() : url + "/" + file.getName();
            Request.Builder builder = new Request.Builder().url(remotePath).put(RequestBody.create(MediaType.parse("application/octet-stream"), file));
            if (!TextUtils.isEmpty(user)) builder.header("Authorization", okhttp3.Credentials.basic(user, pass != null ? pass : ""));
            try (Response resp = client.newCall(builder.build()).execute()) { return resp.isSuccessful(); }
        } catch (Exception e) { return false; }
    }

    public static boolean downloadFile(String url, String user, String pass, String remotePath, File local) {
        try {
            String fullUrl = url.endsWith("/") ? url + remotePath : url + "/" + remotePath;
            Request.Builder builder = new Request.Builder().url(fullUrl);
            if (!TextUtils.isEmpty(user)) builder.header("Authorization", okhttp3.Credentials.basic(user, pass != null ? pass : ""));
            try (Response resp = client.newCall(builder.build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return false;
                try (InputStream is = resp.body().byteStream(); FileOutputStream fos = new FileOutputStream(local)) {
                    byte[] buf = new byte[8192]; int len;
                    while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                }
                return true;
            }
        } catch (Exception e) { return false; }
    }

    public static List<RemoteFile> listFiles(String url, String user, String pass) {
        List<RemoteFile> result = new ArrayList<>();
        try {
            String propfindXml = "<?xml version='1.0'?><d:propfind xmlns:d='DAV:'><d:prop><d:displayname/><d:getlastmodified/><d:resourcetype/></d:prop></d:propfind>";
            Request.Builder builder = new Request.Builder().url(url).method("PROPFIND", RequestBody.create(MediaType.parse("application/xml"), propfindXml));
            if (!TextUtils.isEmpty(user)) builder.header("Authorization", okhttp3.Credentials.basic(user, pass != null ? pass : ""));
            try (Response resp = client.newCall(builder.build()).execute()) {
                if (!resp.isSuccessful() || resp.body() == null) return result;
                String xml = resp.body().string();
                String[] parts = xml.split("<d:response>");
                for (String part : parts) {
                    if (part.contains("<d:href>")) {
                        RemoteFile rf = new RemoteFile();
                        int hrefStart = part.indexOf("<d:href>") + 8;
                        int hrefEnd = part.indexOf("</d:href>");
                        if (hrefStart > 7 && hrefEnd > hrefStart) rf.path = part.substring(hrefStart, hrefEnd);
                        int modStart = part.indexOf("<d:getlastmodified>");
                        if (modStart > 0) {
                            modStart += 19;
                            int modEnd = part.indexOf("</d:getlastmodified>");
                            if (modEnd > modStart) {
                                try { rf.modified = new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", java.util.Locale.US).parse(part.substring(modStart, modEnd)).getTime(); } catch (Exception ignored) {}
                            }
                        }
                        if (!TextUtils.isEmpty(rf.path) && !rf.path.equals(url) && !rf.path.endsWith("/")) result.add(rf);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return result;
    }
}
