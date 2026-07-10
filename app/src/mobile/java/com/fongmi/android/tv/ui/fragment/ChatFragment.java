package com.fongmi.android.tv.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;

public class ChatFragment extends Fragment {

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout buttonContainer;
    private ImageButton btnBack, btnRefresh;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String CHAT_URL = "http://tvm.serv00.net";

    private final ActivityResultLauncher<Intent> filePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (filePathCallback == null) return;
                        Uri[] results = null;
                        if (result.getResultCode() == getActivity().RESULT_OK && result.getData() != null) {
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
        buttonContainer = view.findViewById(R.id.button_container);
        btnBack = view.findViewById(R.id.btn_back);
        btnRefresh = view.findViewById(R.id.btn_refresh);

        setupWebView();
        setupButtons();

        return view;
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(settings.getUserAgentString() + " ChatApp/1.0");
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                ChatFragment.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
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

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                updateBackButtonVisibility();
            }
        });

        webView.loadUrl(CHAT_URL);
    }

    private void setupButtons() {
        // 1. 初始化容器位置（右下角，预留导航栏高度）
        buttonContainer.post(() -> {
            if (getActivity() == null) return;
            int navHeight = getNavBarHeight();
            int marginBottom = navHeight + dpToPx(8);  // 导航栏高度 + 8dp间距
            int marginRight = dpToPx(16);

            View parent = (View) buttonContainer.getParent();
            if (parent == null) return;
            int parentWidth = parent.getWidth();
            int parentHeight = parent.getHeight();

            // 如果父布局尚未测量，等待下一次绘制
            if (parentWidth == 0 || parentHeight == 0) {
                buttonContainer.post(this::setupButtons);
                return;
            }

            int leftMargin = parentWidth - buttonContainer.getWidth() - marginRight;
            int topMargin = parentHeight - buttonContainer.getHeight() - marginBottom;

            leftMargin = Math.max(0, leftMargin);
            topMargin = Math.max(0, topMargin);

            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) buttonContainer.getLayoutParams();
            lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            lp.leftMargin = leftMargin;
            lp.topMargin = topMargin;
            buttonContainer.setLayoutParams(lp);
        });

        // 2. 点击监听
        btnBack.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
                webView.postDelayed(this::updateBackButtonVisibility, 150);
            }
        });

        btnRefresh.setOnClickListener(v -> {
            if (webView != null) {
                webView.reload();
            }
        });

        // 3. 容器拖拽监听
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
                        return isDragging;

                    default:
                        return false;
                }
            }
        });

        updateBackButtonVisibility();
    }

    private void updateBackButtonVisibility() {
        if (btnBack != null && webView != null) {
            btnBack.setVisibility(webView.canGoBack() ? View.VISIBLE : View.GONE);
        }
    }

    private int getNavBarHeight() {
        if (getActivity() == null) return dpToPx(56);
        View navView = getActivity().findViewById(R.id.navigation); // ✅ 使用你的实际ID
        if (navView != null) {
            return navView.getHeight();
        }
        return dpToPx(56); // 默认值
    }

    private int dpToPx(int dp) {
        if (getContext() == null) return dp;
        return (int) (dp * getResources().getDisplayMetrics().density + 0.5f);
    }

    // 由Activity调用，处理物理返回键
    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            updateBackButtonVisibility();
            return true;
        }
        return false;
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
        buttonContainer = null;
        progressBar = null;
        super.onDestroyView();
    }
}