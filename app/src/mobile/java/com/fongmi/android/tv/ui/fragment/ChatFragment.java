package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.Locale;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ChatFragment extends Fragment {

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout buttonContainer;
    private ImageButton btnBack, btnRefresh, btnGo, btnTts;
    private EditText urlInput;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String DEFAULT_URL = "http://tvm.serv00.net";
    private TextToSpeech ttsEngine;
    private boolean isSpeaking = false;
    private boolean ttsInitialized = false;
    private boolean isAttached = false;

    // ==================== 安全防护：URL 安全校验器 ====================
    // 静默拦截不良网站和危险 scheme，不向用户显示提示
    private static final class UrlSafetyChecker {

        // 不良网站关键词黑名单（匹配 URL 域名和路径，大小写不敏感）
        private static final String[] BLOCKED_KEYWORDS = {
            // 赌博
            "casino", "gambl", "bet365", "poker", "roulette", "slot-machine",
            "博彩", "赌博", "时时彩", "六合彩", "澳门赌场", "威尼斯人",
            "金沙", "太阳城", "mgm", "baccarat",
            // 色情
            "porn", "xxx", "sex", "adult", "nude", "hentai", "escort",
            "tube8", "xnxx", "redtube", "youporn", "pornhub", "xvideos",
            "色情", "成人", "裸聊", "一夜情", "av女优",
            // 诈骗/钓鱼
            "phish", "scam", "fraud", "fake-login", "account-verify",
            "free-money", "get-rich", "bitcoin-generator", "crypto-airdrop-scam",
            "诈骗", "钓鱼", "刷单", "兼职日结", "稳赚不赔",
            // 恶意软件
            "malware", "spyware", "trojan", "ransomware", "keylogger",
            "病毒", "木马", "黑客",
            // 暴力/违法
            "drug", "weapon", "kill", "terror", "illegal",
            "毒品", "枪支", "炸弹",
            // 其他不良
            "loan-shark", "payday-loan", "7baidu", "hack-tools"
        };

        // 危险 URL scheme，除 http/https 外全部拦截
        private static final Set<String> BLOCKED_SCHEMES = new HashSet<>(Arrays.asList(
                "javascript:", "data:", "intent:", "file:", "content:",
                "about:", "blob:", "vbscript:", "market:", "ftp:"
        ));

        // 危险文件扩展名（可执行文件等）
        private static final Set<String> BLOCKED_EXTENSIONS = new HashSet<>(Arrays.asList(
                ".apk", ".exe", ".bat", ".sh", ".dll", ".com", ".scr",
                ".msi", ".app", ".command", ".jar", ".vbs", ".ps1",
                ".reg", ".lnk", ".inf", ".gadget", ".hta", ".pif",
                ".wsf", ".wsh", ".msh", ".msh1", ".msh2", ".mshxml"
        ));

        private UrlSafetyChecker() {}

        /** 综合 URL 安全校验，不安全返回 false */
        static boolean isUrlSafe(String url) {
            if (TextUtils.isEmpty(url)) return false;

            String lowerUrl = url.toLowerCase(Locale.getDefault()).trim();

            // 1. 拦截危险 scheme
            for (String scheme : BLOCKED_SCHEMES) {
                if (lowerUrl.startsWith(scheme)) return false;
            }

            // 仅允许 http 和 https
            if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
                return false;
            }

            // 2. 提取域名和路径部分进行关键词匹配
            String hostAndPath;
            try {
                Uri uri = Uri.parse(url);
                String host = uri.getHost();
                String path = uri.getPath();
                String query = uri.getQuery();
                hostAndPath = (host != null ? host.toLowerCase(Locale.getDefault()) : "")
                        + " " + (path != null ? path.toLowerCase(Locale.getDefault()) : "")
                        + " " + (query != null ? query.toLowerCase(Locale.getDefault()) : "");
            } catch (Exception e) {
                hostAndPath = lowerUrl;
            }

            // 3. 匹配不良关键词
            for (String keyword : BLOCKED_KEYWORDS) {
                if (hostAndPath.contains(keyword.toLowerCase(Locale.getDefault()))) {
                    return false;
                }
            }

            return true;
        }

        /** 文件扩展名是否安全（true=安全可下载/上传） */
        static boolean isExtensionSafe(String fileName) {
            if (TextUtils.isEmpty(fileName)) return true; // 无扩展名不拦截
            String lowerName = fileName.toLowerCase(Locale.getDefault());
            for (String ext : BLOCKED_EXTENSIONS) {
                if (lowerName.endsWith(ext)) return false;
            }
            return true;
        }

        /** 净化文件名，去除路径穿越字符和控制字符 */
        static String sanitizeFileName(String name) {
            if (TextUtils.isEmpty(name)) return "download_" + System.currentTimeMillis();
            // 去除路径分隔符和穿越字符
            String sanitized = name.replaceAll("[/\\\\]", "_");
            sanitized = sanitized.replace("..", "_");
            // 去除控制字符
            sanitized = sanitized.replaceAll("[\\x00-\\x1f\\x7f]", "");
            // 去除首尾空格和点
            sanitized = sanitized.replaceAll("^[.\\s]+|[.\\s]+$", "");
            if (TextUtils.isEmpty(sanitized)) {
                sanitized = "download_" + System.currentTimeMillis();
            }
            return sanitized;
        }
    }

    // ==================== 安全防护：文件上传校验器 ====================
    private static final class FileUploadChecker {

        // 允许上传的 MIME 类型前缀
        private static final String[] ALLOWED_MIME_PREFIXES = {
                "image/", "video/", "audio/", "text/"
        };

        // 允许上传的完整 MIME 类型
        private static final Set<String> ALLOWED_MIME_EXACT = new HashSet<>(Arrays.asList(
                "application/pdf",
                "application/zip", "application/x-zip-compressed",
                "application/x-rar-compressed", "application/rar",
                "application/x-7z-compressed",
                "application/gzip", "application/x-gzip",
                "application/x-tar",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/json", "application/xml",
                "application/octet-stream" // 部分设备返回通用类型，后续靠扩展名二次校验
        ));

        // 单文件最大 100MB
        private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

        private FileUploadChecker() {}

        /** 校验上传文件是否安全（MIME 类型 + 文件大小 + 文件来源 + 扩展名） */
        static boolean isFileSafe(Context ctx, Uri uri, String[] acceptTypes) {
            if (uri == null) return false;

            String scheme = uri.getScheme();
            if (scheme == null) return false;

            // 仅允许 content:// 和 file:// 来源
            if (!"content".equals(scheme) && !"file".equals(scheme)) {
                return false;
            }

            // 获取文件名
            String fileName = getFileName(ctx, uri);
            if (!TextUtils.isEmpty(fileName)) {
                // 校验扩展名
                if (!UrlSafetyChecker.isExtensionSafe(fileName)) {
                    return false;
                }
            }

            // 获取并校验文件大小
            long fileSize = getFileSize(ctx, uri);
            if (fileSize > 0 && fileSize > MAX_FILE_SIZE) {
                return false;
            }

            // 获取 MIME 类型
            String mimeType = getMimeType(ctx, uri);

            // 如果网页指定了 accept 类型，校验是否匹配
            if (acceptTypes != null && acceptTypes.length > 0) {
                boolean acceptMatched = false;
                for (String accept : acceptTypes) {
                    if (TextUtils.isEmpty(accept) || "*/*".equals(accept.trim())) {
                        acceptMatched = true;
                        break;
                    }
                    String acceptLower = accept.trim().toLowerCase(Locale.getDefault());
                    if (mimeType != null && mimeType.toLowerCase(Locale.getDefault()).startsWith(acceptLower)) {
                        acceptMatched = true;
                        break;
                    }
                    // 扩展名匹配（如 .jpg）
                    if (acceptLower.startsWith(".") && fileName != null
                            && fileName.toLowerCase(Locale.getDefault()).endsWith(acceptLower)) {
                        acceptMatched = true;
                        break;
                    }
                }
                // accept 不匹配但 MIME 是通用类型时，靠扩展名校验放行
                if (!acceptMatched && !"application/octet-stream".equals(mimeType)) {
                    return false;
                }
            }

            // 校验 MIME 类型白名单
            if (mimeType != null && !mimeType.isEmpty()) {
                String mimeLower = mimeType.toLowerCase(Locale.getDefault());
                boolean mimeAllowed = false;
                for (String prefix : ALLOWED_MIME_PREFIXES) {
                    if (mimeLower.startsWith(prefix)) {
                        mimeAllowed = true;
                        break;
                    }
                }
                if (!mimeAllowed && ALLOWED_MIME_EXACT.contains(mimeLower)) {
                    mimeAllowed = true;
                }
                // application/octet-stream 靠扩展名校验，已在上方处理
                if (!mimeAllowed && !"application/octet-stream".equals(mimeLower)) {
                    return false;
                }
            }

            return true;
        }

        private static String getFileName(Context ctx, Uri uri) {
            if (ctx == null) return null;
            String fileName = null;
            // 优先通过 ContentResolver 查询
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = ctx.getContentResolver().query(
                        uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            // 回退：从 URI 最后一段提取
            if (TextUtils.isEmpty(fileName)) {
                String lastSegment = uri.getLastPathSegment();
                fileName = lastSegment != null ? lastSegment : "";
            }
            return fileName;
        }

        private static long getFileSize(Context ctx, Uri uri) {
            if (ctx == null) return 0;
            long size = 0;
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = ctx.getContentResolver().query(
                        uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                        if (sizeIndex >= 0) {
                            size = cursor.getLong(sizeIndex);
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }
            } else if ("file".equals(uri.getScheme())) {
                try {
                    File file = new File(uri.getPath());
                    if (file.exists()) size = file.length();
                } catch (Exception e) {
                    // ignore
                }
            }
            return size;
        }

        private static String getMimeType(Context ctx, Uri uri) {
            if (ctx == null) return null;
            String mimeType = null;
            if ("content".equals(uri.getScheme())) {
                try {
                    mimeType = ctx.getContentResolver().getType(uri);
                } catch (Exception e) {
                    // ignore
                }
            }
            if (TextUtils.isEmpty(mimeType)) {
                String fileName = getFileName(ctx, uri);
                if (!TextUtils.isEmpty(fileName)) {
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        String ext = fileName.substring(dotIndex + 1).toLowerCase(Locale.getDefault());
                        mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                    }
                }
            }
            return mimeType;
        }
    }

    // ==================== Fragment 生命周期 ====================

    // 保存网页 accept 参数供校验使用（必须在 filePickerLauncher 之前声明，避免前向引用编译错误）
    private String[] lastAcceptTypes = null;

    // 文件选择器（上传）
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (filePathCallback == null) return;
                        Uri[] results = null;
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri dataUri = result.getData().getData();
                            if (dataUri != null) {
                                // 安全校验：检查上传文件是否安全
                                if (getContext() != null
                                        && FileUploadChecker.isFileSafe(getContext(), dataUri, lastAcceptTypes)) {
                                    results = new Uri[]{dataUri};
                                } else {
                                    // 不安全文件，静默拒绝
                                    filePathCallback.onReceiveValue(null);
                                    filePathCallback = null;
                                    return;
                                }
                            }
                        }
                        filePathCallback.onReceiveValue(results);
                        filePathCallback = null;
                        lastAcceptTypes = null;
                    });

    public static ChatFragment newInstance() {
        return new ChatFragment();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        isAttached = true;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        isAttached = false;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        webView = view.findViewById(R.id.webview);
        progressBar = view.findViewById(R.id.progressBar);
        buttonContainer = view.findViewById(R.id.button_container);
        btnBack = view.findViewById(R.id.btn_back);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        urlInput = view.findViewById(R.id.url_input);
        btnGo = view.findViewById(R.id.btn_go);
        btnTts = view.findViewById(R.id.btn_tts);

        setupWebView();
        setupUrlBar();
        setupButtons();

        return view;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " ChatApp/1.0");

        // ===== 安全加固：关闭危险的文件访问和跨域 =====
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        // 保留 ContentAccess 为 true，避免影响部分网站的 content:// 资源加载
        settings.setSavePassword(false);

        // 禁止 JavaScript 自动打开窗口
        settings.setJavaScriptCanOpenWindowsAutomatically(false);

        // 禁止混合内容（HTTPS 页面不允许加载 HTTP 资源）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        // 关闭 WebView 调试模式（生产环境安全）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        // 启用安全浏览（API 26+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

        // ----- 上传文件支持（带安全校验） -----
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                ChatFragment.this.filePathCallback = filePathCallback;
                // 保存 accept 参数供校验使用
                ChatFragment.this.lastAcceptTypes = fileChooserParams.getAcceptTypes();
                Intent intent = fileChooserParams.createIntent();
                try {
                    if (isAttached && getContext() != null) {
                        Uri downloadUri;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                            downloadUri = getContext().getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        } else {
                            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                            if (downloadDir != null && downloadDir.exists()) {
                                downloadUri = Uri.fromFile(downloadDir);
                            } else {
                                downloadUri = Uri.parse("file://" + Environment.getExternalStorageDirectory().getPath() + "/Download");
                            }
                        }
                        intent.putExtra(Intent.EXTRA_TITLE, "选择文件");
                        if (intent.getData() == null) {
                            intent.setDataAndType(downloadUri, "file/*");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                filePickerLauncher.launch(intent);
                return true;
            }

            // 拦截可疑 JS 弹窗（静默拒绝）
            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                result.cancel();
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) {
                result.cancel();
                return true;
            }
        });

        // ----- 下载支持（带安全校验，无需存储权限） -----
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (!isAttached || getContext() == null) return;

                // 安全校验：URL 是否安全
                if (!UrlSafetyChecker.isUrlSafe(url)) {
                    return; // 静默拦截不安全下载
                }

                // 提取并校验文件名
                String fileName = getFileNameFromUrl(url, contentDisposition);
                if (!UrlSafetyChecker.isExtensionSafe(fileName)) {
                    return; // 静默拦截危险文件类型
                }
                // 净化文件名
                fileName = UrlSafetyChecker.sanitizeFileName(fileName);

                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    request.allowScanningByMediaScanner();
                    request.setMimeType(mimetype);

                    String ua = userAgent;
                    if (ua == null || ua.isEmpty()) {
                        ua = webView.getSettings().getUserAgentString();
                    }
                    request.addRequestHeader("User-Agent", ua);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) {
                        request.addRequestHeader("Cookie", cookies);
                    }

                    DownloadManager dm = (DownloadManager) getContext().getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    if (isAttached && getContext() != null) {
                        Toast.makeText(getContext(), "下载已开始，文件保存在 Download 目录", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (isAttached && getContext() != null) {
                        Toast.makeText(getContext(), "下载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // 安全校验：拦截不良网站和危险 scheme（静默拦截）
                if (!UrlSafetyChecker.isUrlSafe(url)) {
                    return true; // 不加载该 URL
                }
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (urlInput != null) {
                    urlInput.setText(url);
                }
                updateBackButtonVisibility();
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }
        });

        // 加载默认 URL
        webView.loadUrl(DEFAULT_URL);
        urlInput.setText(DEFAULT_URL);
    }

    // 地址栏交互
    private void setupUrlBar() {
        // 点击"前往"按钮
        btnGo.setOnClickListener(v -> loadUrlFromInput());
        btnTts.setOnClickListener(v -> toggleTts());

        // 键盘回车触发
        urlInput.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                loadUrlFromInput();
                return true;
            }
            return false;
        });
        initTtsEngine();
    }

    // ==================== TTS 语音朗读 ====================
    private void initTtsEngine() {
        if (ttsEngine != null) return;
        Context ctx = getContext();
        if (ctx == null) return;
        ttsEngine = new TextToSpeech(ctx, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = ttsEngine.setLanguage(Locale.getDefault());
                ttsInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
        ttsEngine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        isSpeaking = false;
                        updateTtsIcon();
                    });
                }
            }
            @Override public void onError(String utteranceId, int errorCode) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        isSpeaking = false;
                        updateTtsIcon();
                    });
                }
            }
            @Override public void onError(String utteranceId) {}
        });
    }

    private void toggleTts() {
        if (!ttsInitialized || ttsEngine == null) {
            Toast.makeText(getContext(), "语音引擎未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isSpeaking) {
            stopTts();
        } else {
            startTts();
        }
    }

    private void startTts() {
        if (webView == null) return;
        // 通过 JavaScript 获取网页正文内容
        webView.evaluateJavascript("(function() { return document.body.innerText; })();", value -> {
            if (value == null || value.equals("null") || TextUtils.isEmpty(value.trim())) {
                Toast.makeText(getContext(), "没有可朗读的文本", Toast.LENGTH_SHORT).show();
                return;
            }
            // 去掉引号（JS 返回的是带引号的字符串）
            String text = value;
            if (text.startsWith(""") && text.endsWith(""")) {
                text = text.substring(1, text.length() - 1);
            }
            // 转义处理
            text = text.replace("\n", "
").replace("\t", "	").replace("\"", """);
            text = text.trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(getContext(), "没有可朗读的文本", Toast.LENGTH_SHORT).show();
                return;
            }
            // 分块朗读
            int maxLen = 4000;
            isSpeaking = true;
            updateTtsIcon();
            if (text.length() <= maxLen) {
                ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_utterance");
            } else {
                ttsEngine.speak(text.substring(0, maxLen), TextToSpeech.QUEUE_FLUSH, null, "tts_utterance_0");
                int start = maxLen, idx = 1;
                while (start < text.length()) {
                    int end = Math.min(start + maxLen, text.length());
                    ttsEngine.speak(text.substring(start, end), TextToSpeech.QUEUE_ADD, null, "tts_utterance_" + idx);
                    start = end;
                    idx++;
                }
            }
        });
    }

    private void stopTts() {
        if (ttsEngine != null) ttsEngine.stop();
        isSpeaking = false;
        updateTtsIcon();
    }

    private void updateTtsIcon() {
        if (btnTts == null) return;
        btnTts.setImageResource(isSpeaking ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
    }

    private void loadUrlFromInput() {
        String url = urlInput.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            if (getContext() != null) Toast.makeText(getContext(), "请输入网址", Toast.LENGTH_SHORT).show();
            return;
        }
        // 自动补全协议
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        // 安全校验：拦截不良网站（静默拦截，不加载不提示）
        if (!UrlSafetyChecker.isUrlSafe(url)) {
            urlInput.clearFocus();
            if (getActivity() != null) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
                }
            }
            return;
        }
        webView.loadUrl(url);
        // 隐藏软键盘
        urlInput.clearFocus();
        if (getActivity() != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
            }
        }
    }

    private void setupButtons() {
        if (buttonContainer == null) return;

        // 初始化位置（右下角，考虑地址栏高度）
        buttonContainer.post(() -> {
            if (!isAttached || getActivity() == null) return;
            int navHeight = getNavBarHeight();
            int marginBottom = navHeight + dpToPx(8);
            int marginRight = dpToPx(16);
            int extraOffset = dpToPx(60); // 偏移量，避免与地址栏重叠（但地址栏在顶部，不影响底部）

            View parent = (View) buttonContainer.getParent();
            if (parent == null) return;
            int parentWidth = parent.getWidth();
            int parentHeight = parent.getHeight();
            if (parentWidth == 0 || parentHeight == 0) {
                buttonContainer.post(this::setupButtons);
                return;
            }

            int leftMargin = parentWidth - buttonContainer.getWidth() - marginRight;
            int topMargin = parentHeight - buttonContainer.getHeight() - marginBottom - extraOffset;
            topMargin = Math.max(0, topMargin);
            leftMargin = Math.max(0, leftMargin);

            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) buttonContainer.getLayoutParams();
            lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            lp.leftMargin = leftMargin;
            lp.topMargin = topMargin;
            buttonContainer.setLayoutParams(lp);
        });

        // 点击监听
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    webView.postDelayed(this::updateBackButtonVisibility, 150);
                }
            });
        }

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                if (webView != null) {
                    webView.reload();
                }
            });
        }

        // 拖拽监听
        buttonContainer.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private float lastX, lastY;
            private boolean isDragging;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        lastX = startX;
                        lastY = startY;
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastX;
                        float dy = event.getRawY() - lastY;
                        float totalDx = event.getRawX() - startX;
                        float totalDy = event.getRawY() - startY;

                        if (Math.abs(totalDx) > 10 || Math.abs(totalDy) > 10) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
                            int newLeft = lp.leftMargin + (int) dx;
                            int newTop = lp.topMargin + (int) dy;

                            View parent = (View) v.getParent();
                            if (parent == null) return true;
                            int parentWidth = parent.getWidth();
                            int parentHeight = parent.getHeight();
                            int navHeight = getNavBarHeight();

                            int maxX = parentWidth - v.getWidth();
                            int maxY = parentHeight - v.getHeight() - navHeight - dpToPx(8);
                            newLeft = Math.max(0, Math.min(newLeft, maxX));
                            newTop = Math.max(0, Math.min(newTop, maxY));

                            lp.leftMargin = newLeft;
                            lp.topMargin = newTop;
                            v.setLayoutParams(lp);

                            lastX = event.getRawX();
                            lastY = event.getRawY();
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging && btnRefresh != null && btnBack != null) {
                            float x = event.getX();
                            float y = event.getY();

                            int refreshTop = btnRefresh.getTop();
                            int refreshBottom = btnRefresh.getBottom();
                            int backTop = btnBack.getTop();
                            int backBottom = btnBack.getBottom();

                            if (y >= refreshTop && y <= refreshBottom) {
                                btnRefresh.performClick();
                            } else if (y >= backTop && y <= backBottom) {
                                btnBack.performClick();
                            }
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });

        updateBackButtonVisibility();
    }

    private void updateBackButtonVisibility() {
        if (!isAttached || btnBack == null || webView == null) return;
        btnBack.setVisibility(webView.canGoBack() ? View.VISIBLE : View.GONE);
    }

    private int getNavBarHeight() {
        if (!isAttached || getActivity() == null) return dpToPx(56);
        View navView = getActivity().findViewById(R.id.navigation);
        if (navView != null) {
            return navView.getHeight();
        }
        return dpToPx(56);
    }

    private int dpToPx(int dp) {
        if (!isAttached || getContext() == null) return dp;
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    public boolean onBackPressed() {
        if (!isAttached || webView == null) return false;
        if (webView.canGoBack()) {
            webView.goBack();
            updateBackButtonVisibility();
            return true;
        }
        return false;
    }

    // ===== 从 URL 或 Content-Disposition 中提取文件名 =====
    private String getFileNameFromUrl(String url, String contentDisposition) {
        String filename = "download_" + System.currentTimeMillis();
        if (contentDisposition != null) {
            String[] parts = contentDisposition.split(";");
            for (String part : parts) {
                if (part.trim().startsWith("filename=")) {
                    String temp = part.trim().substring("filename=".length());
                    if (temp.startsWith("\"") && temp.endsWith("\"")) {
                        temp = temp.substring(1, temp.length() - 1);
                    }
                    filename = temp;
                    break;
                }
            }
        } else if (url != null) {
            try {
                String[] segments = url.split("/");
                String last = segments[segments.length - 1];
                if (last.contains("?")) {
                    last = last.substring(0, last.indexOf("?"));
                }
                if (!last.isEmpty()) {
                    filename = last;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return filename;
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.loadData("", "text/html", "utf-8");
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        btnBack = null;
        btnRefresh = null;
        btnGo = null;
        urlInput = null;
        buttonContainer = null;
        progressBar = null;
        lastAcceptTypes = null;
        super.onDestroyView();
    }
}
