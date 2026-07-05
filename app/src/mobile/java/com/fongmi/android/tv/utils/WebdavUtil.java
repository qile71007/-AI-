package com.fongmi.android.tv.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebdavUtil {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    public static boolean testConnection(String url, String user, String pass) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", Credentials.basic(user, pass))
                    .method("PROPFIND", null)
                    .header("Depth", "0")
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean uploadFile(String url, String user, String pass, File file) {
        try {
            String remotePath = "/webhtv-backup/" + file.getName();
            String fullUrl = url.replaceAll("/+$", "") + remotePath;
            RequestBody body = RequestBody.create(file, MediaType.parse("application/octet-stream"));
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", Credentials.basic(user, pass))
                    .put(body)
                    .build();
            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean downloadFile(String url, String user, String pass, String remotePath, File dest) {
        try {
            String fullUrl = url.replaceAll("/+$", "") + remotePath;
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .header("Authorization", Credentials.basic(user, pass))
                    .get()
                    .build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) return false;
                InputStream is = response.body().byteStream();
                try (FileOutputStream fos = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static List<RemoteFile> listFiles(String url, String user, String pass) {
        ListResult res = listFilesWithDebug(url, user, pass);
        return res.files != null ? res.files : new ArrayList<>();
    }

    public static ListResult listFilesWithDebug(String url, String user, String pass) {
        ListResult result = new ListResult();
        result.files = new ArrayList<>();
        try {
            String baseUrl = url.replaceAll("/+$", "") + "/webhtv-backup/";
            Request request = new Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", Credentials.basic(user, pass))
                    .method("PROPFIND", null)
                    .header("Depth", "1")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    result.rawXml = "HTTP Error: " + response.code();
                    return result;
                }

                String xml = response.body().string();
                result.rawXml = xml;

                Pattern hrefPattern = Pattern.compile("<([a-zA-Z0-9]+:)?href>([^<]*)</([a-zA-Z0-9]+:)?href>", Pattern.CASE_INSENSITIVE);
                Matcher hrefMatcher = hrefPattern.matcher(xml);
                List<String> hrefs = new ArrayList<>();
                while (hrefMatcher.find()) {
                    hrefs.add(hrefMatcher.group(2).trim());
                }

                Pattern lastPattern = Pattern.compile("<([a-zA-Z0-9]+:)?getlastmodified>([^<]*)</([a-zA-Z0-9]+:)?getlastmodified>", Pattern.CASE_INSENSITIVE);
                Matcher lastMatcher = lastPattern.matcher(xml);
                List<String> lastModifieds = new ArrayList<>();
                while (lastMatcher.find()) {
                    lastModifieds.add(lastMatcher.group(2).trim());
                }

                Pattern sizePattern = Pattern.compile("<([a-zA-Z0-9]+:)?getcontentlength>([^<]*)</([a-zA-Z0-9]+:)?getcontentlength>", Pattern.CASE_INSENSITIVE);
                Matcher sizeMatcher = sizePattern.matcher(xml);
                List<String> contentLengths = new ArrayList<>();
                while (sizeMatcher.find()) {
                    contentLengths.add(sizeMatcher.group(2).trim());
                }

                for (int i = 0; i < hrefs.size(); i++) {
                    String href = hrefs.get(i);
                    if (href == null || href.endsWith("/")) continue;

                    String fileName = href;
                    int lastSlash = fileName.lastIndexOf('/');
                    if (lastSlash >= 0) fileName = fileName.substring(lastSlash + 1);
                    int queryIdx = fileName.indexOf('?');
                    if (queryIdx >= 0) fileName = fileName.substring(0, queryIdx);

                    if (fileName.endsWith(".bk.gz") || fileName.endsWith(".zip")) {
                        RemoteFile file = new RemoteFile();
                        file.path = "/webhtv-backup/" + fileName;
                        if (i < lastModifieds.size()) {
                            try {
                                SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                                file.modified = sdf.parse(lastModifieds.get(i)).getTime();
                            } catch (Exception e) {
                                file.modified = System.currentTimeMillis();
                            }
                        } else {
                            file.modified = System.currentTimeMillis();
                        }
                        if (i < contentLengths.size()) {
                            try {
                                file.size = Long.parseLong(contentLengths.get(i));
                            } catch (NumberFormatException ignored) {}
                        }
                        result.files.add(file);
                    }
                }
            }
        } catch (Exception e) {
            result.rawXml = "Exception: " + e.getMessage();
        }
        return result;
    }

    public static class RemoteFile {
        public String path;
        public long modified;
        public long size;
    }

    public static class ListResult {
        public String rawXml;
        public List<RemoteFile> files;
    }
}
