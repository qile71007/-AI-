package com.fongmi.android.tv.utils;

import android.text.TextUtils;
import com.github.catvod.net.OkHttp;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Response;

public class WebdavUtil {
    public static class RemoteFile { public String path; public long modified; }
    public static boolean testConnection(String url, String user, String pass) { return true; }
    public static boolean uploadFile(String url, String user, String pass, File file) { return false; }
    public static boolean downloadFile(String url, String user, String pass, String remotePath, File local) { return false; }
    public static List<RemoteFile> listFiles(String url, String user, String pass) { return new ArrayList<>(); }
}
