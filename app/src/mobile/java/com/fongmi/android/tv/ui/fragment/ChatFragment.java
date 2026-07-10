package com.fongmi.android.tv.ui.fragment;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.fongmi.android.tv.R;

import java.io.File;

public class ChatFragment extends Fragment {

    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout buttonContainer;
    private ImageButton btnBack, btnRefresh;
    private ValueCallback<Uri[]> filePathCallback;
    private static final String CHAT_URL = "http://tvm.serv00.net";
    private boolean isAttached = false; // 标记 Fragment 是否已 attach

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

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (!isGranted && isAttached) {
                            Toast.makeText(getContext(), "需要存储权限才能下载文件", Toast.LENGTH_SHORT).show();
                        }
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
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (!isAttached || getContext() == null) return;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            != PackageManager.PERMISSION_GRANTED) {
                        requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                        return;
                    }
                }
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, getFileNameFromUrl(url, contentDisposition));
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
                    if (isAttached) {
                        Toast.makeText(getContext(), "下载已开始，文件保存在 Download 目录", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    if (isAttached) {
                        Toast.makeText(getContext(), "下载失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                }
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
            String[] segments = url.split("/");
            String last = segments[segments.length - 1];
            if (last.contains("?")) {
                last = last.substring(0, last.indexOf("?"));
            }
            if (!last.isEmpty()) {
                filename = last;
            }
        }
        return filename;
    }

    private void setupButtons() {
        if (buttonContainer == null) return;

        buttonContainer.post(() -> {
            if (!isAttached || getActivity() == null) return;
            int navHeight = getNavBarHeight();
            int marginBottom = navHeight + dpToPx(8);
            int marginRight = dpToPx(16);
            int extraOffset = dpToPx(60);

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
        // 确保 Fragment 已 attach，避免崩溃
        if (!isAttached || btnBack == null || webView == null) return;
        btnBack.setVisibility(webView.canGoBack() ? View.VISIBLE : View.GONE);
    }

    private int getNavBarHeight() {
        // 确保 Fragment 已 attach
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
        // 安全处理：检查 Fragment 是否已 attach 且 webView 可用
        if (!isAttached || webView == null) return false;
        if (webView.canGoBack()) {
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
