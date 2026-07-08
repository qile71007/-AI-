package com.fongmi.android.tv.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;

public class ChatFragment extends Fragment {

    private WebView webView;
    private ProgressBar progressBar;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String CHAT_URL = "http://tvmovie.hostingem.ru/index.php";

    // 使用 ActivityResultLauncher 启动文件选择器
    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (filePathCallback == null) return;
                        Uri[] results = null;
                        if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
                            // 处理多选，但我们只选一张
                            Uri dataUri = result.getData().getData();
                            if (dataUri != null) {
                                results = new Uri[]{dataUri};
                            }
                        }
                        filePathCallback.onReceiveValue(results);
                        filePathCallback = null;
                    });

    public static ChatFragment newInstance() {
        return new ChatFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat, container, false);
        webView = view.findViewById(R.id.webview);
        progressBar = view.findViewById(R.id.progressBar);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " ChatApp/1.0");
        // 允许文件访问
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // 设置 WebChromeClient 处理文件选择
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // 保存回调
                ChatFragment.this.filePathCallback = filePathCallback;
                // 启动文件选择器
                Intent intent = fileChooserParams.createIntent();
                // 设置 MIME 类型为图片
                intent.setType("image/*");
                filePickerLauncher.launch(intent);
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });

        webView.loadUrl(CHAT_URL);
        return view;
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.loadData("", "text/html", "utf-8");
            webView.clearHistory();
            webView.destroy();
        }
        // 清理回调，避免内存泄漏
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        super.onDestroyView();
    }

    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }
}
