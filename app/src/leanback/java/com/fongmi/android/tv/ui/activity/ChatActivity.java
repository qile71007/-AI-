package com.fongmi.android.tv.ui.activity;

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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityChatBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ChatActivity extends BaseActivity {

    private ActivityChatBinding mBinding;
    private WebView mWebView;
    private ProgressBar mProgressBar;
    private EditText mUrlInput;
    private ImageButton mBtnGo, mBtnTts, mBtnRefresh, mBtnBack;
    private ValueCallback<Uri[]> mFilePathCallback;
    private TextToSpeech mTtsEngine;
    private boolean mIsSpeaking = false;
    private boolean mTtsInitialized = false;
    private String[] mLastAcceptTypes;
    private static final String DEFAULT_URL = "http://tvm.serv00.net";

    // ==================== 安全防护：URL 安全校验器 ====================
    private static final class UrlSafetyChecker {
        private static final String[] BLOCKED_KEYWORDS = {
            "casino","gambl","bet365","poker","roulette","slot-machine",
            "博彩","赌博","时时彩","六合彩","澳门赌场","威尼斯人","金沙","太阳城","mgm","baccarat",
            "porn","xxx","sex","adult","nude","hentai","escort","tube8","xnxx","redtube","youporn","pornhub","xvideos",
            "色情","成人","裸聊","一夜情","av女优",
            "phish","scam","fraud","fake-login","account-verify","free-money","get-rich","bitcoin-generator","crypto-airdrop-scam",
            "诈骗","钓鱼","刷单","兼职日结","稳赚不赔",
            "malware","spyware","trojan","ransomware","keylogger","病毒","木马","黑客",
            "drug","weapon","kill","terror","illegal","毒品","枪支","炸弹",
            "loan-shark","payday-loan","7baidu","hack-tools"
        };
        private static final Set<String> BLOCKED_SCHEMES = new HashSet<>(Arrays.asList(
            "javascript:","data:","intent:","file:","content:","about:","blob:","vbscript:","market:","ftp:"
        ));
        private static final Set<String> BLOCKED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".apk",".exe",".bat",".sh",".dll",".com",".scr",".msi",".app",".command",".jar",".vbs",".ps1",".reg",".lnk",".inf",".hta",".pif",".wsf",".wsh",".msh",".msh1",".msh2",".mshxml"
        ));
        static boolean isUrlSafe(String url) {
            if (TextUtils.isEmpty(url)) return false;
            String lowerUrl = url.toLowerCase(Locale.getDefault()).trim();
            for (String scheme : BLOCKED_SCHEMES) if (lowerUrl.startsWith(scheme)) return false;
            if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) return false;
            String hostAndPath;
            try {
                Uri uri = Uri.parse(url);
                hostAndPath = (uri.getHost() != null ? uri.getHost().toLowerCase(Locale.getDefault()) : "")
                        + " " + (uri.getPath() != null ? uri.getPath().toLowerCase(Locale.getDefault()) : "")
                        + " " + (uri.getQuery() != null ? uri.getQuery().toLowerCase(Locale.getDefault()) : "");
            } catch (Exception e) { hostAndPath = lowerUrl; }
            for (String keyword : BLOCKED_KEYWORDS) if (hostAndPath.contains(keyword.toLowerCase(Locale.getDefault()))) return false;
            return true;
        }
        static boolean isExtensionSafe(String fileName) {
            if (TextUtils.isEmpty(fileName)) return true;
            String lowerName = fileName.toLowerCase(Locale.getDefault());
            for (String ext : BLOCKED_EXTENSIONS) if (lowerName.endsWith(ext)) return false;
            return true;
        }
        static String sanitizeFileName(String name) {
            if (TextUtils.isEmpty(name)) return "download_" + System.currentTimeMillis();
            String sanitized = name.replaceAll("[/\\\\]", "_").replace("..", "_");
            sanitized = sanitized.replaceAll("[\\x00-\\x1f\\x7f]", "").replaceAll("^[.\\s]+|[.\\s]+$", "");
            return TextUtils.isEmpty(sanitized) ? "download_" + System.currentTimeMillis() : sanitized;
        }
    }

    // ==================== 安全防护：文件上传校验器 ====================
    private static final class FileUploadChecker {
        private static final String[] ALLOWED_MIME_PREFIXES = {"image/","video/","audio/","text/"};
        private static final Set<String> ALLOWED_MIME_EXACT = new HashSet<>(Arrays.asList(
            "application/pdf","application/zip","application/x-zip-compressed","application/x-rar-compressed","application/rar",
            "application/x-7z-compressed","application/gzip","application/x-gzip","application/x-tar",
            "application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/json","application/xml","application/octet-stream"
        ));
        private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;
        static boolean isFileSafe(Context ctx, Uri uri, String[] acceptTypes) {
            if (uri == null) return false;
            String scheme = uri.getScheme();
            if (scheme == null) return false;
            if (!"content".equals(scheme) && !"file".equals(scheme)) return false;
            String fileName = getFileName(ctx, uri);
            if (!TextUtils.isEmpty(fileName) && !UrlSafetyChecker.isExtensionSafe(fileName)) return false;
            long fileSize = getFileSize(ctx, uri);
            if (fileSize > 0 && fileSize > MAX_FILE_SIZE) return false;
            String mimeType = getMimeType(ctx, uri);
            if (acceptTypes != null && acceptTypes.length > 0) {
                boolean acceptMatched = false;
                for (String accept : acceptTypes) {
                    if (TextUtils.isEmpty(accept) || "*/*".equals(accept.trim())) { acceptMatched = true; break; }
                    String acceptLower = accept.trim().toLowerCase(Locale.getDefault());
                    if (mimeType != null && mimeType.toLowerCase(Locale.getDefault()).startsWith(acceptLower)) { acceptMatched = true; break; }
                    if (acceptLower.startsWith(".") && fileName != null && fileName.toLowerCase(Locale.getDefault()).endsWith(acceptLower)) { acceptMatched = true; break; }
                }
                if (!acceptMatched && !"application/octet-stream".equals(mimeType)) return false;
            }
            if (mimeType != null && !mimeType.isEmpty()) {
                String mimeLower = mimeType.toLowerCase(Locale.getDefault());
                boolean mimeAllowed = false;
                for (String prefix : ALLOWED_MIME_PREFIXES) { if (mimeLower.startsWith(prefix)) { mimeAllowed = true; break; } }
                if (!mimeAllowed && ALLOWED_MIME_EXACT.contains(mimeLower)) mimeAllowed = true;
                if (!mimeAllowed && !"application/octet-stream".equals(mimeLower)) return false;
            }
            return true;
        }
        private static String getFileName(Context ctx, Uri uri) {
            String fileName = null;
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        if (nameIndex >= 0) fileName = cursor.getString(nameIndex);
                    }
                } catch (Exception ignored) {}
            }
            if (TextUtils.isEmpty(fileName)) fileName = uri.getLastPathSegment();
            return fileName;
        }
        private static long getFileSize(Context ctx, Uri uri) {
            if ("content".equals(uri.getScheme())) {
                try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                        if (sizeIndex >= 0) return cursor.getLong(sizeIndex);
                    }
                } catch (Exception ignored) {}
            }
            return 0;
        }
        private static String getMimeType(Context ctx, Uri uri) {
            String mimeType = null;
            if ("content".equals(uri.getScheme())) { try { mimeType = ctx.getContentResolver().getType(uri); } catch (Exception ignored) {} }
            if (TextUtils.isEmpty(mimeType)) {
                String fileName = getFileName(ctx, uri);
                if (!TextUtils.isEmpty(fileName)) {
                    int dotIndex = fileName.lastIndexOf('.');
                    if (dotIndex > 0) mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substring(dotIndex + 1).toLowerCase(Locale.getDefault()));
                }
            }
            return mimeType;
        }
    }

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ChatActivity.class));
    }

    public static void start(Activity activity, String url) {
        Intent intent = new Intent(activity, ChatActivity.class);
        if (!TextUtils.isEmpty(url)) intent.putExtra("url", url);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityChatBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mWebView = mBinding.webview;
        mProgressBar = mBinding.progressBar;
        mUrlInput = mBinding.urlInput;
        mBtnGo = mBinding.btnGo;
        mBtnTts = mBinding.btnTts;
        mBtnRefresh = mBinding.btnRefresh;
        mBtnBack = mBinding.btnBack;
        setupWebView();
        initTtsEngine();
        String url = getIntent().getStringExtra("url");
        loadUrl(!TextUtils.isEmpty(url) ? url : DEFAULT_URL);
    }

    @Override
    protected void initEvent() {
        mBtnGo.setOnClickListener(v -> loadUrlFromInput());
        mBtnTts.setOnClickListener(v -> toggleTts());
        mBtnRefresh.setOnClickListener(v -> mWebView.reload());
        mBtnBack.setOnClickListener(v -> { if (mWebView.canGoBack()) mWebView.goBack(); });
        mUrlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                loadUrlFromInput();
                return true;
            }
            return false;
        });
    }

    private void setupWebView() {
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " ChatApp/1.0");
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSavePassword(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) WebView.setWebContentsDebuggingEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) settings.setSafeBrowsingEnabled(true);

        // ----- WebChromeClient：上传文件（带安全校验）+ 拦截 JS 弹窗 -----
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> filePathCallback, FileChooserParams params) {
                mFilePathCallback = filePathCallback;
                mLastAcceptTypes = params.getAcceptTypes();
                Intent intent = params.createIntent();
                try {
                    Uri downloadUri;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                        downloadUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    } else {
                        downloadUri = Uri.parse("file://" + Environment.getExternalStorageDirectory().getPath() + "/Download");
                    }
                    intent.setDataAndType(downloadUri, "file/*");
                } catch (Exception ignored) {}
                mFilePickerLauncher.launch(intent);
                return true;
            }
            @Override public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) { result.cancel(); return true; }
            @Override public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) { result.cancel(); return true; }
            @Override public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, android.webkit.JsPromptResult result) { result.cancel(); return true; }
        });

        // ----- 下载支持（带安全校验）-----
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (!UrlSafetyChecker.isUrlSafe(url)) return; // 静默拦截不安全 URL
            String fileName = UrlSafetyChecker.sanitizeFileName(getFileNameFromUrl(url, contentDisposition));
            if (!UrlSafetyChecker.isExtensionSafe(fileName)) return; // 静默拦截危险文件类型
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                request.allowScanningByMediaScanner();
                request.setMimeType(mimetype);
                String ua = userAgent;
                if (TextUtils.isEmpty(ua)) ua = mWebView.getSettings().getUserAgentString();
                request.addRequestHeader("User-Agent", ua);
                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) request.addRequestHeader("Cookie", cookies);
                ((DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this, R.string.chat_download_started, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.chat_download_failed, Toast.LENGTH_SHORT).show();
            }
        });

        // ----- WebViewClient：安全浏览拦截 -----
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!UrlSafetyChecker.isUrlSafe(url)) return true; // 静默拦截
                view.loadUrl(url);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) { super.onPageStarted(view, url, favicon); mProgressBar.setVisibility(View.VISIBLE); }
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                mProgressBar.setVisibility(View.GONE);
                mUrlInput.setText(url);
                mBtnBack.setVisibility(mWebView.canGoBack() ? View.VISIBLE : View.GONE);
            }
        });
    }

    private final ActivityResultLauncher<Intent> mFilePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (mFilePathCallback == null) return;
                Uri[] results = null;
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri dataUri = result.getData().getData();
                    if (dataUri != null && FileUploadChecker.isFileSafe(this, dataUri, mLastAcceptTypes)) {
                        results = new Uri[]{dataUri};
                    }
                }
                mFilePathCallback.onReceiveValue(results);
                mFilePathCallback = null;
                mLastAcceptTypes = null;
            });

    private void loadUrl(String url) {
        if (!UrlSafetyChecker.isUrlSafe(url)) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        mWebView.loadUrl(url);
        mUrlInput.setText(url);
    }

    private void loadUrlFromInput() {
        String url = mUrlInput.getText().toString().trim();
        if (TextUtils.isEmpty(url)) return;
        if (!UrlSafetyChecker.isUrlSafe(url)) return; // 静默拦截
        loadUrl(url);
    }

    private String getFileNameFromUrl(String url, String contentDisposition) {
        String fileName = "download_" + System.currentTimeMillis();
        if (!TextUtils.isEmpty(contentDisposition)) {
            for (String part : contentDisposition.split(";")) {
                part = part.trim();
                if (part.startsWith("filename=")) {
                    fileName = part.substring(9).replace("\"", "").trim();
                    break;
                }
            }
        }
        if (TextUtils.isEmpty(fileName) || fileName.equals("download_")) {
            String path = Uri.parse(url).getLastPathSegment();
            if (!TextUtils.isEmpty(path)) fileName = path;
        }
        return fileName;
    }

    private void initTtsEngine() {
        if (mTtsEngine != null) return;
        mTtsEngine = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = mTtsEngine.setLanguage(Locale.getDefault());
                mTtsInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
        mTtsEngine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) { runOnUiThread(() -> { mIsSpeaking = false; updateTtsIcon(); }); }
            @Override public void onError(String utteranceId, int errorCode) { runOnUiThread(() -> { mIsSpeaking = false; updateTtsIcon(); }); }
            @Override public void onError(String utteranceId) { runOnUiThread(() -> { mIsSpeaking = false; updateTtsIcon(); }); }
        });
    }

    private void toggleTts() {
        if (!mTtsInitialized || mTtsEngine == null) {
            Toast.makeText(this, R.string.chat_tts_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIsSpeaking) { mTtsEngine.stop(); mIsSpeaking = false; }
        else {
            mWebView.evaluateJavascript(
                "(function(){var s=window.getSelection()+'';return s.trim()?s:document.body.innerText;})();",
                value -> {
                    if (value == null || value.equals("null")) return;
                    String text = value;
                    if (text.startsWith("\"") && text.endsWith("\""))
                        text = text.substring(1, text.length() - 1);
                    text = text.replace("\\n", "\n").replace("\\t", "\t")
                             .replace("\\\"", "\"").trim();
                    if (TextUtils.isEmpty(text)) return;
                    mIsSpeaking = true;
                    updateTtsIcon();
                    int maxLen = 4000;
                    if (text.length() <= maxLen) {
                        mTtsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts");
                    } else {
                        mTtsEngine.speak(text.substring(0, maxLen), TextToSpeech.QUEUE_FLUSH, null, "tts_0");
                        for (int i = 1, start = maxLen; start < text.length(); i++, start += maxLen) {
                            int end = Math.min(start + maxLen, text.length());
                            mTtsEngine.speak(text.substring(start, end), TextToSpeech.QUEUE_ADD, null, "tts_" + i);
                        }
                    }
                });
        }
        updateTtsIcon();
    }

    private void updateTtsIcon() {
        mBtnTts.setImageResource(mIsSpeaking ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
    }

    @Override
    protected void onDestroy() {
        if (mTtsEngine != null) { mTtsEngine.stop(); mTtsEngine.shutdown(); }
        super.onDestroy();
    }
}
