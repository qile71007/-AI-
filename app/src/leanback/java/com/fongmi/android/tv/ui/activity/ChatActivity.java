package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import com.fongmi.android.tv.utils.KeyUtil;

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
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }

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
        });

        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "download_" + System.currentTimeMillis());
                DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) dm.enqueue(request);
                Toast.makeText(this, R.string.chat_download_started, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.chat_download_failed, Toast.LENGTH_SHORT).show();
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                mProgressBar.setVisibility(View.VISIBLE);
            }
            @Override
            public void onPageFinished(WebView view, String url) {
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
                    if (dataUri != null) results = new Uri[]{dataUri};
                }
                mFilePathCallback.onReceiveValue(results);
                mFilePathCallback = null;
            });

    private void loadUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        mWebView.loadUrl(url);
        mUrlInput.setText(url);
    }

    private void loadUrlFromInput() {
        String url = mUrlInput.getText().toString().trim();
        if (TextUtils.isEmpty(url)) return;
        loadUrl(url);
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
