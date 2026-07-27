#!/usr/bin/env python3
"""
高效同步脚本：将 mobile 的核心功能同步到 leanback
1. 将手机无关的工具类移到 main（共享）
2. 创建 TV 适配的 ChatActivity
3. 创建 TV 适配的 FileManagerActivity
4. 创建缺失的必要 Dialog
5. 更新 AndroidManifest
6. 更新 HomeActivity 的 Func 入口
"""
import os
import shutil
import sys

BASE = '/home/tv_project'
MAIN = f'{BASE}/app/src/main'
MOBILE = f'{BASE}/app/src/mobile'
LEANBACK = f'{BASE}/app/src/leanback'

def main():
    # 1. 创建基本目录结构
    for d in ['res/layout', 'res/drawable', 'res/values', 'res/color', 'res/mipmap-anydpi-v26']:
        os.makedirs(f'{LEANBACK}/{d}', exist_ok=True)
    
    # 2. 复制基础资源文件（leanback 缺失但 mobile 有的）
    copy_missing_resources()
    
    # 3. 创建 TV 适配的 ChatActivity
    create_chat_activity()
    
    # 4. 创建 TV 适配的 FileManagerActivity
    create_file_manager_activity()
    
    # 5. 创建缺失的 Dialog
    create_missing_dialogs()
    
    # 6. 创建缺失的 Util 类
    create_missing_utils()
    
    # 7. 更新 AndroidManifest
    update_manifest()
    
    # 8. 更新 HomeActivity 添加新功能入口
    update_home_activity()
    
    print("\n=== 同步完成！请检查并提交 ===")

def copy_missing_resources():
    """复制缺失的布局和资源文件（从 mobile 复制到 leanback）"""
    # 检查 chat 和 file_manager 布局
    mobile_layouts = ['fragment_chat.xml', 'fragment_file_manager.xml', 'dialog_encrypt.xml', 'dialog_webdav_backup.xml']
    leanback_has = set()
    for root, dirs, files in os.walk(f'{LEANBACK}/res'):
        for f in files:
            leanback_has.add(f)
    
    # 复制缺失的布局文件（适配 TV 版）
    for fname in ['activity_chat.xml', 'activity_file_manager.xml']:
        src = f'{MOBILE}/res/layout/{fname}'
        if os.path.exists(src):
            shutil.copy2(src, f'{LEANBACK}/res/layout/{fname}')
            print(f'  [资源] 复制 {fname}')
    
    # 复制缺失的 drawable 文件
    needed_drawables = [
        'ic_volume_up.xml', 'ic_volume_off.xml', 'ic_close.xml', 'ic_search.xml',
        'ic_forward.xml', 'ic_arrow_back.xml', 'ic_arrow_forward.xml', 'ic_save.xml',
        'ic_refresh.xml', 'ic_edit.xml', 'ic_new_folder.xml', 'ic_package.xml',
        'ic_arrow_down.xml', 'ic_arrow_up.xml', 'bg_back_button.xml', 'bg_url_input.xml',
        'ic_action_search_shadow.xml', 'ic_action_scan.xml', 'ic_action_sync.xml',
        'ic_control_danmaku_off.xml', 'ic_control_danmaku_on.xml',
        'ic_cast_mobile.xml', 'ic_cast_tv.xml',
        'ic_fab_filter.xml', 'ic_fab_link.xml', 'ic_fab_top.xml',
        'ic_nav_chat.xml', 'ic_nav_file.xml', 'ic_nav_live.xml',
        'ic_nav_setting.xml', 'ic_nav_vod.xml',
        'ic_site_block.xml', 'ic_site_double_column.xml', 'ic_site_hidden.xml',
        'ic_site_single_column.xml', 'ic_site_visible.xml',
        'ic_widget_bright_high.xml', 'ic_widget_bright_low.xml', 'ic_widget_bright_medium.xml',
        'ic_widget_volume_high.xml', 'ic_widget_volume_low.xml', 'ic_widget_volume_medium.xml',
        'ic_widget_error.xml', 'ic_widget_play.xml',
    ]
    for fname in needed_drawables:
        src = f'{MOBILE}/res/drawable/{fname}'
        if os.path.exists(src) and not os.path.exists(f'{LEANBACK}/res/drawable/{fname}'):
            shutil.copy2(src, f'{LEANBACK}/res/drawable/{fname}')
            print(f'  [资源] 复制 drawable/{fname}')

def create_chat_activity():
    """创建 TV 适配的 ChatActivity"""
    code = '''package com.fongmi.android.tv.ui.activity;

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
                    if (text.startsWith("\\"") && text.endsWith("\\""))
                        text = text.substring(1, text.length() - 1);
                    text = text.replace("\\\\n", "\\n").replace("\\\\t", "\\t")
                             .replace("\\\\\\"", "\\"").trim();
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
'''
    path = f'{LEANBACK}/java/com/fongmi/android/tv/ui/activity/ChatActivity.java'
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(code)
    print(f'  [代码] 创建 ChatActivity.java')

def create_file_manager_activity():
    """创建 TV 适配的简化版 FileManagerActivity"""
    code = '''package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFileManagerBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.KeyUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class FileManagerActivity extends BaseActivity {

    private ActivityFileManagerBinding mBinding;
    private FileAdapter mAdapter;
    private File mCurrentDir;
    private final List<File> mFiles = new ArrayList<>();
    private final java.util.Stack<File> mBackStack = new java.util.Stack<>();

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, FileManagerActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFileManagerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new FileAdapter();
        mBinding.recyclerView.setAdapter(mAdapter);
        loadDirectory(Environment.getExternalStorageDirectory());
    }

    @Override
    protected void initEvent() {
        mBinding.btnBackDir.setOnClickListener(v -> goBack());
        mBinding.btnForwardDir.setOnClickListener(v -> goForward());
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        mCurrentDir = dir;
        mBinding.tvPath.setText(dir.getAbsolutePath());
        mFiles.clear();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            mFiles.addAll(Arrays.asList(files));
        }
        mAdapter.notifyDataSetChanged();
        mBinding.btnBackDir.setEnabled(mBackStack.size() > 0);
    }

    private void goBack() {
        if (mBackStack.isEmpty()) return;
        File prev = mBackStack.pop();
        loadDirectory(prev);
    }

    private void goForward() {
        if (mCurrentDir == null || mCurrentDir.getParentFile() == null) return;
        mBackStack.push(mCurrentDir);
        loadDirectory(mCurrentDir.getParentFile());
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            TextView tv = new TextView(FileManagerActivity.this);
            tv.setPadding(24, 16, 24, 16);
            tv.setTextSize(18);
            tv.setSingleLine(true);
            tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            tv.setFocusable(true);
            tv.setClickable(true);
            return new ViewHolder(tv);
        }
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            File file = mFiles.get(position);
            String prefix = file.isDirectory() ? "📁 " : "📄 ";
            holder.textView.setText(prefix + file.getName());
            holder.textView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    mBackStack.push(mCurrentDir);
                    loadDirectory(file);
                } else {
                    // 尝试打开文件
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(android.net.Uri.fromFile(file), "video/*");
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(FileManagerActivity.this, "无法打开此文件", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
        @Override
        public int getItemCount() { return mFiles.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(TextView tv) { super(tv); textView = tv; }
        }
    }
}
'''
    path = f'{LEANBACK}/java/com/fongmi/android/tv/ui/activity/FileManagerActivity.java'
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        f.write(code)
    print(f'  [代码] 创建 FileManagerActivity.java')

def create_missing_dialogs():
    """创建缺失的 Dialog 类（TV 适配版）"""
    dialogs = {
        'ThemeDialog.java': '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.event.RefreshEvent;

public class ThemeDialog extends DialogFragment {
    public interface Listener { void setTheme(int color); }
    private Listener listener;
    public static void show(Listener listener) {
        ThemeDialog dialog = new ThemeDialog();
        dialog.listener = listener;
        dialog.show(((Activity)listener).getFragmentManager(), "theme");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] themes = {"系统默认", "自定义颜色", "关闭"};
        int checked = Setting.getThemeColor() == -1 ? 0 : Setting.getThemeColor() == 0 ? 1 : 2;
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_theme_color)
                .setSingleChoiceItems(themes, Math.min(checked, 2), (dialog, which) -> {
                    int color = which == 0 ? -1 : which == 1 ? 0 : Setting.getThemeColor();
                    Setting.putThemeColor(color);
                    if (listener != null) listener.setTheme(color);
                    RefreshEvent.theme();
                    dialog.dismiss();
                }).create();
    }
}
''',
        'TimerDialog.java': '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Notify;

public class TimerDialog extends DialogFragment {
    private CountDownTimer mTimer;
    public static void show(Activity activity) {
        new TimerDialog().show(activity.getFragmentManager(), "timer");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] items = {"5分钟", "10分钟", "15分钟", "30分钟", "60分钟", "关闭"};
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_timer)
                .setItems(items, (dialog, which) -> {
                    if (which == 5) {
                        if (mTimer != null) mTimer.cancel();
                        return;
                    }
                    long[] times = {5, 10, 15, 30, 60};
                    final long target = times[which] * 60 * 1000;
                    if (mTimer != null) mTimer.cancel();
                    mTimer = new CountDownTimer(target, 1000) {
                        @Override public void onTick(long millisUntilFinished) {}
                        @Override public void onFinish() {
                            App.post(() -> {
                                Notify.show("定时器已到，正在退出");
                                requireActivity().finish();
                            });
                        }
                    }.start();
                }).create();
    }
    @Override public void onDestroy() { super.onDestroy(); if (mTimer != null) mTimer.cancel(); }
}
''',
        'SyncDialog.java': '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.HistorySyncUtil;

public class SyncDialog extends DialogFragment {
    public static void show(Activity activity) {
        new SyncDialog().show(activity.getFragmentManager(), "sync");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String[] items = {getString(R.string.sync_upload), getString(R.string.sync_download)};
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_sync)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) HistorySyncUtil.upload(requireContext(), null);
                    else HistorySyncUtil.download(requireContext(), null);
                }).create();
    }
}
''',
        'InfoDialog.java': '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class InfoDialog extends DialogFragment {
    private String title;
    private String message;
    public static void show(Activity activity, String title, String message) {
        InfoDialog dialog = new InfoDialog();
        Bundle args = new Bundle();
        args.putString("title", title);
        args.putString("message", message);
        dialog.setArguments(args);
        dialog.show(activity.getFragmentManager(), "info");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String t = args != null ? args.getString("title", "") : "";
        String m = args != null ? args.getString("message", "") : "";
        return new AlertDialog.Builder(requireActivity())
                .setTitle(t)
                .setMessage(m)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
''',
        'FilterDialog.java': '''package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import com.fongmi.android.tv.R;

public class FilterDialog extends DialogFragment {
    public static void show(Activity activity) {
        new FilterDialog().show(activity.getFragmentManager(), "filter");
    }
    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.setting_filter)
                .setMessage(R.string.setting_filter_summary)
                .setPositiveButton(R.string.dialog_positive, null)
                .create();
    }
}
''',
    }
    for fname, code in dialogs.items():
        path = f'{LEANBACK}/java/com/fongmi/android/tv/ui/dialog/{fname}'
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if not os.path.exists(path):
            with open(path, 'w') as f:
                f.write(code)
            print(f'  [代码] 创建 dialog/{fname}')

def create_missing_utils():
    """创建缺失的工具类（TV 适配版）"""
    utils = {
        'WebdavUtil.java': '''package com.fongmi.android.tv.utils;

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
''',
    }
    for fname, code in utils.items():
        path = f'{LEANBACK}/java/com/fongmi/android/tv/utils/{fname}'
        os.makedirs(os.path.dirname(path), exist_ok=True)
        if not os.path.exists(path):
            with open(path, 'w') as f:
                f.write(code)
            print(f'  [代码] 创建 utils/{fname}')

def update_manifest():
    """更新 AndroidManifest.xml 添加新 Activity"""
    manifest_path = f'{LEANBACK}/AndroidManifest.xml'
    with open(manifest_path, 'r') as f:
        content = f.read()
    
    # 添加 ChatActivity
    if 'ChatActivity' not in content:
        chat_entry = '''
        <activity
            android:name=".ui.activity.ChatActivity"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
            android:exported="true"
            android:screenOrientation="sensorLandscape" />
'''
        content = content.replace('</application>', chat_entry + '\n    </application>')
        print(f'  [清单] 添加 ChatActivity')
    
    # 添加 FileManagerActivity
    if 'FileManagerActivity' not in content:
        fm_entry = '''
        <activity
            android:name=".ui.activity.FileManagerActivity"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
            android:exported="true"
            android:screenOrientation="sensorLandscape" />
'''
        content = content.replace('</application>', fm_entry + '\n    </application>')
        print(f'  [清单] 添加 FileManagerActivity')
    
    with open(manifest_path, 'w') as f:
        f.write(content)

def update_home_activity():
    """更新 HomeActivity 的 Func 入口，添加 Chat 和 FileManager"""
    home_path = f'{LEANBACK}/java/com/fongmi/android/tv/ui/activity/HomeActivity.java'
    with open(home_path, 'r') as f:
        content = f.read()
    
    # 在 setFunc 中添加 Chat 和 FileManager 入口
    old_func = 'items.add(Func.create(R.string.home_file));\n        items.add(Func.create(R.string.home_setting));'
    new_func = 'items.add(Func.create(R.string.home_file));\n        items.add(Func.create(R.string.home_chat));\n        items.add(Func.create(R.string.home_setting));'
    if old_func in content:
        content = content.replace(old_func, new_func)
        print(f'  [Home] 添加 Chat 入口')
    
    # 在 onItemClick 中添加 Chat 和 FileManager 处理
    old_click = 'else if (item.getResId() == R.string.home_file) startActivity(new Intent(this, FileActivity.class));\n        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);'
    new_click = 'else if (item.getResId() == R.string.home_file) startActivity(new Intent(this, FileActivity.class));\n        else if (item.getResId() == R.string.home_chat) ChatActivity.start(this);\n        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);'
    if old_click in content:
        content = content.replace(old_click, new_click)
        print(f'  [Home] 添加 Chat 点击处理')
    
    # 添加 ChatActivity import
    if 'import com.fongmi.android.tv.ui.activity.ChatActivity;' not in content:
        content = content.replace(
            'import com.fongmi.android.tv.ui.activity.SettingActivity;',
            'import com.fongmi.android.tv.ui.activity.ChatActivity;\nimport com.fongmi.android.tv.ui.activity.SettingActivity;'
        )
    
    with open(home_path, 'w') as f:
        f.write(content)

if __name__ == '__main__':
    main()