package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public final class LocalProxyDebug {

    private static final String TAG = "local-proxy";
    private static final long MIN_DUMP_INTERVAL_MS = 5000;
    private static final int MAX_TAIL_BYTES = 32 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 250;
    private static final int CONNECT_INTERVAL_MS = 200;
    private static final AtomicLong LAST_DUMP = new AtomicLong();

    private LocalProxyDebug() {
    }

    public static void dumpIfLocalFailure(String url, Throwable error) {
        if (!DebugLogStore.isEnabled() || !isLocalProxyUrl(url)) return;
        long now = System.currentTimeMillis();
        long last = LAST_DUMP.get();
        if (now - last < MIN_DUMP_INTERVAL_MS || !LAST_DUMP.compareAndSet(last, now)) return;
        Task.execute(() -> dump(url, error));
    }

    public static boolean shouldAwaitReady(String url) {
        return isLocalProxyUrl(url) && getPort(url) > 0;
    }

    public static boolean awaitReady(String url, long timeoutMs) {
        String host = getHost(url);
        int port = getPort(url);
        if (TextUtils.isEmpty(host) || port <= 0) return true;
        long start = System.currentTimeMillis();
        long deadline = start + timeoutMs;
        int attempt = 0;
        while (System.currentTimeMillis() <= deadline) {
            attempt++;
            if (canConnect(host, port)) {
                SpiderDebug.log(TAG, "ready host=%s port=%d urlLen=%d attempt=%d elapsed=%dms", host, port, url.length(), attempt, System.currentTimeMillis() - start);
                return true;
            }
            sleep(CONNECT_INTERVAL_MS);
        }
        SpiderDebug.log(TAG, "ready timeout host=%s port=%d urlLen=%d attempts=%d elapsed=%dms", host, port, url.length(), attempt, System.currentTimeMillis() - start);
        return false;
    }

    public static boolean isConnectionRefused(Throwable error) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth++ < 8) {
            String message = current.getMessage();
            if (current instanceof java.net.ConnectException) return true;
            if (message != null && message.contains("ECONNREFUSED")) return true;
            if (message != null && message.contains("Connection refused")) return true;
            current = current.getCause();
        }
        return false;
    }

    public static boolean isLocalProxyUrl(String url) {
        if (TextUtils.isEmpty(url)) return false;
        return url.startsWith("http://127.0.0.1") || url.startsWith("http://localhost");
    }

    private static String getHost(String url) {
        try {
            return Uri.parse(url).getHost();
        } catch (Throwable e) {
            return "";
        }
    }

    private static int getPort(String url) {
        try {
            return Uri.parse(url).getPort();
        } catch (Throwable e) {
            return -1;
        }
    }

    private static boolean canConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void dump(String url, Throwable error) {
        SpiderDebug.log(TAG, "diagnose start host=%s port=%d urlLen=%d error=%s", getHost(url), getPort(url), url == null ? 0 : url.length(), error == null ? "" : error.getMessage());
        dumpDir(Path.files());
        dumpLog(new File(Path.files(), "goProxy.log"));
        dumpLog(new File(Path.cache(), "goProxy.log"));
        dumpProcessSnapshot();
        SpiderDebug.log(TAG, "diagnose end");
    }

    private static void dumpDir(File dir) {
        try {
            File[] files = dir.listFiles((file, name) -> {
                String lower = name.toLowerCase(Locale.US);
                return lower.contains("goproxy") || lower.endsWith(".log");
            });
            if (files == null || files.length == 0) {
                SpiderDebug.log(TAG, "files snapshot dir=%s empty", dir.getAbsolutePath());
                return;
            }
            StringBuilder builder = new StringBuilder();
            for (File file : files) {
                if (builder.length() > 0) builder.append('\n');
                builder.append(file.getName())
                        .append(" size=").append(file.length())
                        .append(" modified=").append(time(file.lastModified()))
                        .append(" read=").append(file.canRead())
                        .append(" exec=").append(file.canExecute());
            }
            SpiderDebug.log(TAG, "files snapshot dir=%s\n%s", dir.getAbsolutePath(), builder);
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "files snapshot failed dir=%s error=%s", dir, e.getMessage());
        }
    }

    private static void dumpLog(File file) {
        try {
            if (!file.exists()) {
                SpiderDebug.log(TAG, "log missing file=%s", file.getAbsolutePath());
                return;
            }
            SpiderDebug.log(TAG, "log tail file=%s size=%s modified=%s\n%s", file.getAbsolutePath(), file.length(), time(file.lastModified()), tail(file));
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "log read failed file=%s error=%s", file.getAbsolutePath(), e.getMessage());
        }
    }

    private static String tail(File file) throws Exception {
        byte[] data = Path.readToByte(file);
        int start = Math.max(0, data.length - MAX_TAIL_BYTES);
        return new String(data, start, data.length - start, "UTF-8");
    }

    private static void dumpProcessSnapshot() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ps -A | grep -E 'goProxy|goProxy_arm|tgsou' || true"});
            String output = read(process.getInputStream());
            String error = read(process.getErrorStream());
            int code = process.waitFor();
            SpiderDebug.log(TAG, "process snapshot code=%s\nstdout:\n%s\nstderr:\n%s", code, empty(output), empty(error));
        } catch (Throwable e) {
            SpiderDebug.log(TAG, "process snapshot failed error=%s", e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String read(InputStream stream) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(stream); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1 && output.size() < 12000) output.write(buffer, 0, read);
            return output.toString("UTF-8");
        }
    }

    private static String empty(String value) {
        return TextUtils.isEmpty(value) ? "(empty)" : value.trim();
    }

    private static String time(long millis) {
        if (millis <= 0) return "n/a";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(millis));
    }
}
