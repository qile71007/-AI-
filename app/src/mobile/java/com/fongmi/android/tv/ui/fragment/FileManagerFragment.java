package com.fongmi.android.tv.ui.fragment;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.ImagePreviewActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Stack;

import okhttp3.Call;
import okhttp3.Response;

public class FileManagerFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvPath;
    private ImageButton btnBackDir, btnForwardDir, btnNewFolder;
    private LinearLayout buttonContainer;
    private ImageButton btnEdit;
    private FileAdapter adapter;
    private File currentDir;
    private List<File> fileList = new ArrayList<>();
    private File copiedFile = null;
    private boolean isAttached = false;

    // 导航历史栈
    private Stack<File> backStack = new Stack<>();
    private Stack<File> forwardStack = new Stack<>();

    private LinearLayout fullscreenEditorContainer;
    private EditText fullscreenEditor;
    private TextView fullscreenTitle;
    private ImageButton btnCloseEditor, btnSaveEditor, btnSearchToggle, btnJump;
    private View searchLayout;
    private EditText searchEditText;
    private TextView searchCount;
    private ImageButton btnSearchPrev, btnSearchNext, btnSearchClose;

    private File currentConfigFile = null;
    private String currentConfigType = "";
    private String currentConfigUrl = "";
    private boolean isRemoteConfig = false;

    // 搜索相关
    private List<Integer> matchPositions = new ArrayList<>();
    private int currentMatchIndex = -1;
    private String lastSearchKeyword = null;
    private boolean isPerformingSearch = false;

    // 拖拽按钮最大重试次数
    private static final int MAX_DRAG_SETUP_RETRIES = 10;
    private int dragSetupRetryCount = 0;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            loadDirectory(Environment.getExternalStorageDirectory(), false);
                        } else {
                            Toast.makeText(getContext(), "需要存储权限才能访问文件", Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (Environment.isExternalStorageManager()) {
                                loadDirectory(Environment.getExternalStorageDirectory(), false);
                            } else {
                                Toast.makeText(getContext(), "需要授予「所有文件访问权限」", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    public static FileManagerFragment newInstance() {
        return new FileManagerFragment();
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
        View view = inflater.inflate(R.layout.fragment_file_manager, container, false);

        // 强制非空获取所有 View，布局缺失时会明确报错，避免 NPE
        recyclerView = Objects.requireNonNull(view.findViewById(R.id.recyclerView), "recyclerView not found");
        tvPath = Objects.requireNonNull(view.findViewById(R.id.tv_path), "tv_path not found");
        btnBackDir = Objects.requireNonNull(view.findViewById(R.id.btn_back_dir), "btn_back_dir not found");
        btnForwardDir = Objects.requireNonNull(view.findViewById(R.id.btn_forward_dir), "btn_forward_dir not found");
        btnNewFolder = Objects.requireNonNull(view.findViewById(R.id.btn_new_folder), "btn_new_folder not found");
        buttonContainer = Objects.requireNonNull(view.findViewById(R.id.button_container), "button_container not found");
        btnEdit = Objects.requireNonNull(view.findViewById(R.id.btn_edit), "btn_edit not found");
        fullscreenEditorContainer = Objects.requireNonNull(view.findViewById(R.id.fullscreen_editor_container), "fullscreen_editor_container not found");

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        btnBackDir.setOnClickListener(v -> goBack());
        btnForwardDir.setOnClickListener(v -> goForward());
        btnNewFolder.setOnClickListener(v -> newFolder());
        btnEdit.setOnClickListener(v -> editCurrentConfig());

        setupDragButton();
        setupFullscreenEditor();
        detectCurrentConfig();

        checkPermissionsAndLoad();
        return view;
    }

    private void setupFullscreenEditor() {
        if (fullscreenEditorContainer == null) return;

        fullscreenEditor = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.fullscreen_editor), "fullscreen_editor not found");
        fullscreenTitle = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.fullscreen_title), "fullscreen_title not found");
        btnCloseEditor = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_close_editor), "btn_close_editor not found");
        btnSaveEditor = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_save_editor), "btn_save_editor not found");
        btnSearchToggle = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_editor), "btn_search_editor not found");
        btnJump = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_jump_editor), "btn_jump_editor not found");
        searchLayout = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.search_layout), "search_layout not found");
        searchEditText = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.search_edit_text), "search_edit_text not found");
        searchCount = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.search_count), "search_count not found");
        btnSearchPrev = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_prev), "btn_search_prev not found");
        btnSearchNext = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_next), "btn_search_next not found");
        btnSearchClose = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_close), "btn_search_close not found");

        if (fullscreenEditor != null) {
            fullscreenEditor.setTypeface(android.graphics.Typeface.MONOSPACE);
            fullscreenEditor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (isPerformingSearch) return;
                    clearHighlights();
                    matchPositions.clear();
                    currentMatchIndex = -1;
                    updateSearchCount();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        btnCloseEditor.setOnClickListener(v -> closeFullscreenEditor());
        btnSaveEditor.setOnClickListener(v -> {
            if (fullscreenEditor != null && currentConfigFile != null) {
                saveAndApplyConfig(fullscreenEditor.getText().toString());
                closeFullscreenEditor();
            }
        });
        btnSearchToggle.setOnClickListener(v -> toggleSearchBar());
        btnSearchClose.setOnClickListener(v -> closeSearchBar());
        btnSearchPrev.setOnClickListener(v -> navigateMatch(-1));
        btnSearchNext.setOnClickListener(v -> navigateMatch(1));
        btnJump.setOnClickListener(v -> showJumpDialog());

        // ====== 实时搜索 + 搜索键兼容 ======
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch(searchEditText.getText().toString());
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
                return true;
            }
            return false;
        });
    }

    // ---------- 搜索相关方法 ----------
    private void toggleSearchBar() {
        if (searchLayout.getVisibility() == View.VISIBLE) {
            closeSearchBar();
        } else {
            searchLayout.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void closeSearchBar() {
        searchLayout.setVisibility(View.GONE);
        clearHighlights();
        matchPositions.clear();
        currentMatchIndex = -1;
        updateSearchCount();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
    }

    private void performSearch(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            clearHighlights();
            matchPositions.clear();
            currentMatchIndex = -1;
            updateSearchCount();
            return;
        }
        lastSearchKeyword = keyword;
        String text = fullscreenEditor.getText().toString();
        matchPositions.clear();

        String lowerText = text.toLowerCase();
        String lowerKeyword = keyword.toLowerCase();
        int index = 0;
        while ((index = lowerText.indexOf(lowerKeyword, index)) != -1) {
            matchPositions.add(index);
            index += keyword.length();
        }

        if (matchPositions.isEmpty()) {
            Toast.makeText(getContext(), "未找到匹配项", Toast.LENGTH_SHORT).show();
            clearHighlights();
            currentMatchIndex = -1;
            updateSearchCount();
            return;
        }

        SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        for (int pos : matchPositions) {
            spannable.setSpan(new BackgroundColorSpan(Color.argb(200, 255, 235, 59)),
                    pos, pos + keyword.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        isPerformingSearch = true;
        fullscreenEditor.setText(spannable, TextView.BufferType.EDITABLE);
        fullscreenEditor.invalidate();
        isPerformingSearch = false;

        currentMatchIndex = 0;
        scrollToMatch(currentMatchIndex);
        updateSearchCount();
    }

    private void navigateMatch(int step) {
        if (matchPositions.isEmpty()) {
            Toast.makeText(getContext(), "没有匹配项", Toast.LENGTH_SHORT).show();
            return;
        }
        int newIndex = currentMatchIndex + step;
        if (newIndex < 0) newIndex = matchPositions.size() - 1;
        else if (newIndex >= matchPositions.size()) newIndex = 0;
        currentMatchIndex = newIndex;
        scrollToMatch(currentMatchIndex);
        updateSearchCount();
    }

    private void scrollToMatch(int index) {
        if (index < 0 || index >= matchPositions.size()) return;
        int pos = matchPositions.get(index);
        fullscreenEditor.setSelection(pos, pos + (lastSearchKeyword != null ? lastSearchKeyword.length() : 0));
        fullscreenEditor.requestFocus();
        fullscreenEditor.post(() -> {
            Layout layout = fullscreenEditor.getLayout();
            if (layout != null) {
                int line = layout.getLineForOffset(pos);
                int y = layout.getLineTop(line);
                fullscreenEditor.scrollTo(0, Math.max(0, y - 50));
            }
        });
    }

    private void clearHighlights() {
        if (fullscreenEditor == null) return;
        Editable editable = fullscreenEditor.getText();
        if (editable == null) return;
        BackgroundColorSpan[] spans = editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
        for (BackgroundColorSpan span : spans) {
            editable.removeSpan(span);
        }
    }

    private void updateSearchCount() {
        if (searchCount == null) return;
        int total = matchPositions.size();
        if (total > 0 && currentMatchIndex >= 0 && currentMatchIndex < total) {
            searchCount.setText((currentMatchIndex + 1) + "/" + total);
        } else {
            searchCount.setText(total > 0 ? "0/" + total : "");
        }
        btnSearchPrev.setEnabled(total > 1);
        btnSearchNext.setEnabled(total > 1);
    }

    private void showJumpDialog() {
        if (fullscreenEditor == null) return;
        if (fullscreenEditor.getLayout() == null) {
            fullscreenEditor.post(this::showJumpDialog);
            return;
        }
        int totalLines = fullscreenEditor.getLineCount();
        if (totalLines == 0) {
            Toast.makeText(getContext(), "文档为空", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("跳转到行号 (1-" + totalLines + ")");
        final EditText input = new EditText(requireContext());
        input.setHint("输入行号 (1-" + totalLines + ")");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);
        builder.setPositiveButton("跳转", (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (TextUtils.isEmpty(text)) {
                Toast.makeText(getContext(), "请输入行号", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int lineNumber = Integer.parseInt(text);
                if (lineNumber < 1 || lineNumber > totalLines) {
                    Toast.makeText(getContext(), "行号范围: 1-" + totalLines, Toast.LENGTH_SHORT).show();
                    return;
                }
                jumpToLine(lineNumber);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "请输入有效数字", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void jumpToLine(int lineNumber) {
        int lineIndex = lineNumber - 1;
        Layout layout = fullscreenEditor.getLayout();
        if (layout == null || lineIndex < 0 || lineIndex >= layout.getLineCount()) return;
        int start = layout.getLineStart(lineIndex);
        fullscreenEditor.setSelection(start);
        fullscreenEditor.requestFocus();
        int y = layout.getLineTop(lineIndex);
        fullscreenEditor.scrollTo(0, Math.max(0, y - 20));
        Toast.makeText(getContext(), "已跳转到第 " + lineNumber + " 行", Toast.LENGTH_SHORT).show();
    }

    private void showFullscreenEditor(String content, String fileName) {
        if (fullscreenEditorContainer == null) return;
        String title = "编辑: " + fileName;
        if (isRemoteConfig) {
            title += " (远程)";
        }
        fullscreenTitle.setText(title);
        fullscreenEditor.setText(content);
        fullscreenEditor.post(() -> {
            fullscreenEditor.setSelection(0);
            fullscreenEditor.scrollTo(0, 0);
        });
        fullscreenEditorContainer.setVisibility(View.VISIBLE);
        fullscreenEditorContainer.bringToFront();
        fullscreenEditor.requestFocus();
        if (getActivity() != null) {
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void closeFullscreenEditor() {
        if (fullscreenEditorContainer != null) {
            fullscreenEditorContainer.setVisibility(View.GONE);
            closeSearchBar();
        }
        if (getActivity() != null) {
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        }
    }

    private void setupDragButton() {
        if (buttonContainer == null) return;
        buttonContainer.post(() -> {
            if (!isAttached || getActivity() == null) return;
            int navHeight = getNavBarHeight();
            int marginBottom = navHeight + dpToPx(8);
            int marginRight = dpToPx(16);
            View parent = (View) buttonContainer.getParent();
            if (parent == null) return;
            int parentWidth = parent.getWidth();
            int parentHeight = parent.getHeight();
            if (parentWidth == 0 || parentHeight == 0) {
                if (dragSetupRetryCount < MAX_DRAG_SETUP_RETRIES) {
                    dragSetupRetryCount++;
                    buttonContainer.post(this::setupDragButton);
                } else {
                    dragSetupRetryCount = 0;
                }
                return;
            }
            dragSetupRetryCount = 0;
            int leftMargin = parentWidth - buttonContainer.getWidth() - marginRight;
            int topMargin = parentHeight - buttonContainer.getHeight() - marginBottom;
            topMargin = Math.max(0, topMargin);
            leftMargin = Math.max(0, leftMargin);
            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) buttonContainer.getLayoutParams();
            lp.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            lp.removeRule(RelativeLayout.ALIGN_PARENT_END);
            lp.leftMargin = leftMargin;
            lp.topMargin = topMargin;
            buttonContainer.setLayoutParams(lp);
        });
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
                        if (!isDragging) {
                            btnEdit.performClick();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
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

    private void detectCurrentConfig() {
        try {
            String vodUrl = VodConfig.get().getUrl();
            if (vodUrl != null && !vodUrl.isEmpty()) {
                currentConfigUrl = vodUrl;
                currentConfigType = "vod";
                if (vodUrl.startsWith("file://") || vodUrl.startsWith("/")) {
                    String path = vodUrl.replace("file://", "");
                    currentConfigFile = new File(path);
                    isRemoteConfig = false;
                } else {
                    isRemoteConfig = true;
                    currentConfigFile = null;
                }
                return;
            }
            String liveUrl = LiveConfig.get().getUrl();
            if (liveUrl != null && !liveUrl.isEmpty()) {
                currentConfigUrl = liveUrl;
                currentConfigType = "live";
                if (liveUrl.startsWith("file://") || liveUrl.startsWith("/")) {
                    String path = liveUrl.replace("file://", "");
                    currentConfigFile = new File(path);
                    isRemoteConfig = false;
                } else {
                    isRemoteConfig = true;
                    currentConfigFile = null;
                }
                return;
            }
            String wallUrl = WallConfig.get().getUrl();
            if (wallUrl != null && !wallUrl.isEmpty()) {
                currentConfigUrl = wallUrl;
                currentConfigType = "wall";
                if (wallUrl.startsWith("file://") || wallUrl.startsWith("/")) {
                    String path = wallUrl.replace("file://", "");
                    currentConfigFile = new File(path);
                    isRemoteConfig = false;
                } else {
                    isRemoteConfig = true;
                    currentConfigFile = null;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private File getDownloadConfigFile(String type, String url) {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        String fileName = type + "_";
        try {
            String urlPath = Uri.parse(url).getPath();
            if (urlPath != null && !urlPath.isEmpty()) {
                String[] segments = urlPath.split("/");
                String last = segments[segments.length - 1];
                if (!last.isEmpty() && last.contains(".")) {
                    fileName = type + "_" + last;
                } else {
                    fileName = type + "_" + System.currentTimeMillis() + ".txt";
                }
            } else {
                fileName = type + "_" + System.currentTimeMillis() + ".txt";
            }
        } catch (Exception e) {
            fileName = type + "_" + System.currentTimeMillis() + ".txt";
        }
        if (!fileName.endsWith(".txt") && !fileName.endsWith(".json") &&
                !fileName.endsWith(".m3u") && !fileName.endsWith(".m3u8")) {
            fileName += ".txt";
        }
        return new File(downloadDir, fileName);
    }

    private void downloadAndEditRemoteConfig() {
        if (TextUtils.isEmpty(currentConfigUrl) ||
                (!currentConfigUrl.startsWith("http://") && !currentConfigUrl.startsWith("https://"))) {
            if (isAttached) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), "无效的网络地址: " + currentConfigUrl, Toast.LENGTH_SHORT).show()
                );
            }
            return;
        }
        Toast.makeText(getContext(), "正在下载远程配置...", Toast.LENGTH_SHORT).show();
        final File targetFile = getDownloadConfigFile(currentConfigType, currentConfigUrl);
        OkHttp.get().newCall(currentConfigUrl).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAttached) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().string();
                    if (isAttached) {
                        requireActivity().runOnUiThread(() -> {
                            try {
                                try (FileWriter writer = new FileWriter(targetFile)) {
                                    writer.write(content);
                                    writer.flush();
                                }
                                currentConfigFile = targetFile;
                                isRemoteConfig = false;
                                currentConfigUrl = "file://" + targetFile.getAbsolutePath();
                                Toast.makeText(getContext(),
                                        "下载成功，已保存到:\n" + targetFile.getAbsolutePath(),
                                        Toast.LENGTH_LONG).show();
                                showFullscreenEditor(content, targetFile.getName());
                            } catch (IOException e) {
                                Toast.makeText(getContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                } else {
                    if (isAttached) {
                        requireActivity().runOnUiThread(() ->
                                Toast.makeText(getContext(), "下载失败: HTTP " + response.code(), Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            }
        });
    }

    private void readAndEditLocalFile(File file) {
        if (!file.exists() || !file.canRead()) {
            Toast.makeText(getContext(), "文件不存在或无法读取", Toast.LENGTH_SHORT).show();
            return;
        }
        if (file.length() > 5 * 1024 * 1024) {
            Toast.makeText(getContext(), "文件过大（超过5MB），无法在编辑器中打开", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            Toast.makeText(getContext(), "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.length() == 0) {
            Toast.makeText(getContext(), "文件内容为空", Toast.LENGTH_SHORT).show();
            return;
        }
        showFullscreenEditor(content.toString(), file.getName());
    }

    private void editCurrentConfig() {
        if (fullscreenEditorContainer != null && fullscreenEditorContainer.getVisibility() == View.VISIBLE) {
            return;
        }
        detectCurrentConfig();
        if (!TextUtils.isEmpty(currentConfigType) && !TextUtils.isEmpty(currentConfigUrl)) {
            if (isRemoteConfig) {
                downloadAndEditRemoteConfig();
                return;
            } else if (currentConfigFile != null && currentConfigFile.exists()) {
                readAndEditLocalFile(currentConfigFile);
                return;
            } else {
                if (!TextUtils.isEmpty(currentConfigUrl) &&
                        (currentConfigUrl.startsWith("http://") || currentConfigUrl.startsWith("https://"))) {
                    downloadAndEditRemoteConfig();
                    return;
                } else {
                    Toast.makeText(getContext(), "本地配置文件不存在，请检查文件路径", Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }
        showFilePickerForEdit();
    }

    private void showFilePickerForEdit() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("选择要编辑的文件");
        List<File> fileItems = new ArrayList<>();
        if (currentDir != null && currentDir.listFiles() != null) {
            for (File f : currentDir.listFiles()) {
                if (f.isFile() && !f.getName().startsWith(".")) {
                    fileItems.add(f);
                }
            }
        }
        if (fileItems.isEmpty()) {
            Toast.makeText(getContext(), "当前目录没有可编辑的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] fileNames = new String[fileItems.size()];
        for (int i = 0; i < fileItems.size(); i++) {
            fileNames[i] = fileItems.get(i).getName();
        }
        builder.setItems(fileNames, (dialog, which) -> {
            File selectedFile = fileItems.get(which);
            editTextFile(selectedFile);
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveAndApplyConfig(String content) {
        if (currentConfigFile == null) {
            Toast.makeText(getContext(), "未选择配置文件", Toast.LENGTH_SHORT).show();
            return;
        }
        try (FileWriter writer = new FileWriter(currentConfigFile)) {
            writer.write(content);
            writer.flush();
            Toast.makeText(getContext(), "配置文件保存成功\n" + currentConfigFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            if (isRemoteConfig) {
                isRemoteConfig = false;
                Toast.makeText(getContext(), "已切换为本地配置", Toast.LENGTH_SHORT).show();
            }
            applyConfig(currentConfigFile.getAbsolutePath());
            loadDirectory(currentDir, true);
        } catch (IOException e) {
            Toast.makeText(getContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void applyConfig(String filePath) {
        File configFile = new File(filePath);
        if (!configFile.exists() || !configFile.canRead()) {
            Toast.makeText(getContext(), "配置文件不存在或无法读取", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String content = readFileFirstLines(configFile, 10);
            if (TextUtils.isEmpty(content)) {
                Toast.makeText(getContext(), "配置文件内容为空", Toast.LENGTH_LONG).show();
                return;
            }
            String url = "file://" + filePath;
            int type;
            switch (currentConfigType) {
                case "vod": type = 0; break;
                case "live": type = 1; break;
                case "wall": type = 2; break;
                default:
                    Toast.makeText(getContext(), "未知配置类型: " + currentConfigType, Toast.LENGTH_SHORT).show();
                    return;
            }
            Config config = Config.find(url, type);
            if (config == null) {
                Toast.makeText(getContext(), "无法创建配置记录", Toast.LENGTH_LONG).show();
                return;
            }
            switch (currentConfigType) {
                case "vod": {
                    if (!content.trim().startsWith("{") && !content.trim().startsWith("[")) {
                        Toast.makeText(getContext(), "点播配置格式错误，需为 JSON 格式", Toast.LENGTH_LONG).show();
                        return;
                    }
                    VodConfig.get().load(config, new Callback() {
                        @Override
                        public void success() {
                            if (isAdded()) {
                                Notify.show("点播配置加载成功");
                                Toast.makeText(getContext(), "点播配置已应用", Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new ConfigEvent(ConfigEvent.Type.COMMON));
                                RefreshEvent.home();
                            }
                        }
                        @Override
                        public void error(String msg) {
                            if (isAdded()) {
                                String errorMsg = "点播配置加载失败" + (TextUtils.isEmpty(msg) ? "" : ": " + msg);
                                Notify.show(errorMsg);
                                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    break;
                }
                case "live": {
                    if (!content.contains("#EXTM3U") && !content.contains("http")) {
                        Toast.makeText(getContext(), "直播配置可能不是标准 M3U 或 TXT 格式", Toast.LENGTH_LONG).show();
                    }
                    LiveConfig.get().load(config, new Callback() {
                        @Override
                        public void success() {
                            if (isAdded()) {
                                Notify.show("直播配置加载成功");
                                Toast.makeText(getContext(), "直播配置已应用", Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new ConfigEvent(ConfigEvent.Type.COMMON));
                                RefreshEvent.home();
                            }
                        }
                        @Override
                        public void error(String msg) {
                            if (isAdded()) {
                                String errorMsg = "直播配置加载失败" + (TextUtils.isEmpty(msg) ? "" : ": " + msg);
                                Notify.show(errorMsg);
                                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    break;
                }
                case "wall": {
                    WallConfig.get().load(config, new Callback() {
                        @Override
                        public void success() {
                            if (isAdded()) {
                                Notify.show("壁纸配置加载成功");
                                Toast.makeText(getContext(), "壁纸配置已应用", Toast.LENGTH_SHORT).show();
                                EventBus.getDefault().post(new ConfigEvent(ConfigEvent.Type.COMMON));
                                RefreshEvent.home();
                            }
                        }
                        @Override
                        public void error(String msg) {
                            if (isAdded()) {
                                String errorMsg = "壁纸配置加载失败" + (TextUtils.isEmpty(msg) ? "" : ": " + msg);
                                Notify.show(errorMsg);
                                Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                    break;
                }
            }
            detectCurrentConfig();
        } catch (Exception e) {
            e.printStackTrace();
            String err = "应用配置异常: " + e.getMessage();
            Toast.makeText(getContext(), err, Toast.LENGTH_LONG).show();
            Notify.show(err);
        }
    }

    public void setCurrentConfig(File configFile, String type) {
        if (!isAdded()) return;
        if (configFile == null || !configFile.exists()) {
            Toast.makeText(getContext(), "配置文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        this.currentConfigFile = configFile;
        this.currentConfigType = type;
        this.isRemoteConfig = false;
        this.currentConfigUrl = "file://" + configFile.getAbsolutePath();
        Toast.makeText(getContext(), "正在加载配置...", Toast.LENGTH_SHORT).show();
        applyConfig(configFile.getAbsolutePath());
    }

    private String readFileFirstLines(File file, int lines) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < lines) {
                sb.append(line).append("\n");
                count++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                manageStorageLauncher.launch(intent);
                return;
            } else {
                loadDirectory(Environment.getExternalStorageDirectory(), false);
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                loadDirectory(Environment.getExternalStorageDirectory(), false);
            }
        }
    }

    private void loadDirectory(File dir) {
        loadDirectory(dir, true);
    }

    private void loadDirectory(File dir, boolean addToBackStack) {
        if (dir == null || !dir.exists()) {
            Toast.makeText(getContext(), "目录不存在", Toast.LENGTH_SHORT).show();
            return;
        }
        if (addToBackStack) {
            if (currentDir != null) {
                backStack.push(currentDir);
            }
            forwardStack.clear();
        }
        currentDir = dir;
        tvPath.setText(dir.getAbsolutePath());
        fileList.clear();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().startsWith(".")) {
                    fileList.add(f);
                }
            }
            Collections.sort(fileList, (o1, o2) -> {
                if (o1.isDirectory() && !o2.isDirectory()) return -1;
                if (!o1.isDirectory() && o2.isDirectory()) return 1;
                return o1.getName().compareToIgnoreCase(o2.getName());
            });
        }
        adapter.notifyDataSetChanged();
        updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        if (btnBackDir != null && btnForwardDir != null) {
            btnBackDir.setEnabled(!backStack.isEmpty());
            btnForwardDir.setEnabled(!forwardStack.isEmpty());
            btnBackDir.setAlpha(backStack.isEmpty() ? 0.4f : 1.0f);
            btnForwardDir.setAlpha(forwardStack.isEmpty() ? 0.4f : 1.0f);
        }
    }

    private void goBack() {
        if (!isAdded()) return;
        if (!backStack.isEmpty()) {
            File parent = backStack.pop();
            forwardStack.push(currentDir);
            loadDirectory(parent, false);
        } else {
            Toast.makeText(getContext(), "没有更早的目录", Toast.LENGTH_SHORT).show();
        }
    }

    private void goForward() {
        if (!isAdded()) return;
        if (!forwardStack.isEmpty()) {
            File next = forwardStack.pop();
            backStack.push(currentDir);
            loadDirectory(next, false);
        } else {
            Toast.makeText(getContext(), "没有更后的目录", Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFile(child);
                }
            }
        }
        if (file.delete()) {
            Toast.makeText(getContext(), "删除成功", Toast.LENGTH_SHORT).show();
            loadDirectory(currentDir);
        } else {
            Toast.makeText(getContext(), "删除失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void renameFile(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("重命名");
        final EditText input = new EditText(getContext());
        input.setText(file.getName());
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (TextUtils.isEmpty(newName)) {
                Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            File newFile = new File(file.getParent(), newName);
            if (file.renameTo(newFile)) {
                Toast.makeText(getContext(), "重命名成功", Toast.LENGTH_SHORT).show();
                loadDirectory(currentDir);
            } else {
                Toast.makeText(getContext(), "重命名失败", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void newFolder() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("新建文件夹");
        final EditText input = new EditText(getContext());
        input.setHint("请输入文件夹名称");
        builder.setView(input);
        builder.setPositiveButton("确定", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (TextUtils.isEmpty(name)) {
                Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }
            File newDir = new File(currentDir, name);
            if (newDir.mkdir()) {
                Toast.makeText(getContext(), "创建成功", Toast.LENGTH_SHORT).show();
                loadDirectory(currentDir);
            } else {
                Toast.makeText(getContext(), "创建失败", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void copyFile(File src) {
        copiedFile = src;
        Toast.makeText(getContext(), "已复制 " + src.getName(), Toast.LENGTH_SHORT).show();
    }

    private void pasteFile() {
        if (copiedFile == null || !copiedFile.exists()) {
            Toast.makeText(getContext(), "没有可粘贴的文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (copiedFile.getParent().equals(currentDir.getAbsolutePath())) {
            Toast.makeText(getContext(), "目标与源相同，无法粘贴", Toast.LENGTH_SHORT).show();
            return;
        }
        if (copiedFile.isFile()) {
            File dest = new File(currentDir, copiedFile.getName());
            int count = 1;
            while (dest.exists()) {
                String name = copiedFile.getName();
                int dot = name.lastIndexOf('.');
                String base = (dot == -1) ? name : name.substring(0, dot);
                String ext = (dot == -1) ? "" : name.substring(dot);
                dest = new File(currentDir, base + "(" + count + ")" + ext);
                count++;
            }
            try {
                try (InputStream in = new FileInputStream(copiedFile);
                     OutputStream out = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                }
                Toast.makeText(getContext(), "粘贴成功", Toast.LENGTH_SHORT).show();
                loadDirectory(currentDir);
            } catch (Exception e) {
                Toast.makeText(getContext(), "粘贴失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "暂不支持复制文件夹", Toast.LENGTH_SHORT).show();
        }
    }

    private void editTextFile(File file) {
        if (fullscreenEditorContainer != null && fullscreenEditorContainer.getVisibility() == View.VISIBLE) {
            return;
        }
        if (!file.canRead()) {
            Toast.makeText(getContext(), "无法读取文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (file.length() > 5 * 1024 * 1024) {
            Toast.makeText(getContext(), "文件过大（超过5MB），无法在编辑器中打开", Toast.LENGTH_LONG).show();
            return;
        }
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            Toast.makeText(getContext(), "读取文件失败", Toast.LENGTH_SHORT).show();
            return;
        }
        currentConfigFile = file;
        currentConfigType = detectConfigType(file);
        isRemoteConfig = false;
        currentConfigUrl = "file://" + file.getAbsolutePath();
        showFullscreenEditor(content.toString(), file.getName());
    }

    private boolean isVideoFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mkv") ||
                lower.endsWith(".mov") || lower.endsWith(".wmv") || lower.endsWith(".flv") ||
                lower.endsWith(".3gp") || lower.endsWith(".m4v") || lower.endsWith(".webm") ||
                lower.endsWith(".ts") || lower.endsWith(".mpeg") || lower.endsWith(".mpg") ||
                lower.endsWith(".rmvb") || lower.endsWith(".vob") || lower.endsWith(".ogv");
    }

    private boolean isAudioFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") ||
                lower.endsWith(".aac") || lower.endsWith(".m4a") || lower.endsWith(".ogg") ||
                lower.endsWith(".wma") || lower.endsWith(".ape") || lower.endsWith(".opus");
    }

    private boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".gif") || lower.endsWith(".bmp") || lower.endsWith(".webp") ||
                lower.endsWith(".svg") || lower.endsWith(".ico");
    }

    private boolean isTextFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".txt") || lower.endsWith(".java") || lower.endsWith(".xml") ||
                lower.endsWith(".json") || lower.endsWith(".log") || lower.endsWith(".md") ||
                lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".html") ||
                lower.endsWith(".css") || lower.endsWith(".sh") || lower.endsWith(".properties") ||
                lower.endsWith(".m3u") || lower.endsWith(".m3u8");
    }

    private void playVideo(File file) {
        if (getActivity() == null) return;
        VideoActivity.file(getActivity(), file.getAbsolutePath());
    }

    private void playAudio(File file) {
        if (getActivity() == null) return;
        VideoActivity.file(getActivity(), file.getAbsolutePath());
    }

    private void previewImage(File file) {
        if (getActivity() == null) return;
        Intent intent = new Intent(getActivity(), ImagePreviewActivity.class);
        intent.putExtra("image_path", file.getAbsolutePath());
        startActivity(intent);
    }

    // ==================== Adapter ====================
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = fileList.get(position);
            holder.tvName.setText(file.getName());
            holder.tvInfo.setText(file.isDirectory() ? "文件夹" : formatFileSize(file.length()) + "  " + lastModified(file));
            holder.ivIcon.setImageResource(file.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    loadDirectory(file);
                } else {
                    String name = file.getName();
                    if (isVideoFile(name)) {
                        playVideo(file);
                    } else if (isAudioFile(name)) {
                        playAudio(file);
                    } else if (isImageFile(name)) {
                        previewImage(file);
                    } else if (isTextFile(name)) {
                        editTextFile(file);
                    } else {
                        Toast.makeText(getContext(), "不支持此类型文件", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (!file.isDirectory()) {
                    showFileOptions(file);
                } else {
                    showFolderOptions(file);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return fileList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo;
            ImageButton ivIcon;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = Objects.requireNonNull(itemView.findViewById(R.id.tv_name), "tv_name not found");
                tvInfo = Objects.requireNonNull(itemView.findViewById(R.id.tv_info), "tv_info not found");
                ivIcon = Objects.requireNonNull(itemView.findViewById(R.id.iv_icon), "iv_icon not found");
            }
        }

        private String formatFileSize(long size) {
            if (size < 1024) return size + " B";
            int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
            return String.format(Locale.US, "%.1f %sB", (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
        }

        private String lastModified(File file) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(file.lastModified()));
        }
    }

    private void showFileOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(file.getName());
        String[] items = {"删除", "重命名", "复制", "剪切", "粘贴", "设为当前配置", "编辑", "播放/查看"};
        builder.setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: deleteFile(file); break;
                case 1: renameFile(file); break;
                case 2: copyFile(file); break;
                case 3:
                    copyFile(file);
                    deleteFile(file);
                    break;
                case 4: pasteFile(); break;
                case 5:
                    setCurrentConfig(file, detectConfigType(file));
                    Toast.makeText(getContext(), "已设置为当前配置并加载: " + file.getName(), Toast.LENGTH_SHORT).show();
                    break;
                case 6:
                    if (!file.isDirectory()) editTextFile(file);
                    break;
                case 7:
                    if (!file.isDirectory()) {
                        String name = file.getName();
                        if (isVideoFile(name) || isAudioFile(name)) {
                            playVideo(file);
                        } else if (isImageFile(name)) {
                            previewImage(file);
                        } else {
                            Toast.makeText(getContext(), "此类型不支持播放/查看", Toast.LENGTH_SHORT).show();
                        }
                    }
                    break;
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void showFolderOptions(File file) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(file.getName());
        String[] items = {"删除", "重命名", "复制", "剪切", "粘贴"};
        builder.setItems(items, (dialog, which) -> {
            switch (which) {
                case 0: deleteFile(file); break;
                case 1: renameFile(file); break;
                case 2: copyFile(file); break;
                case 3:
                    copyFile(file);
                    deleteFile(file);
                    break;
                case 4: pasteFile(); break;
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private String detectConfigType(File file) {
        String name = file.getName().toLowerCase();
        if (name.contains("vod") || name.contains("点播") || name.endsWith(".json")) {
            return "vod";
        } else if (name.contains("live") || name.contains("直播") || name.endsWith(".m3u") || name.endsWith(".m3u8")) {
            return "live";
        } else if (name.contains("wall") || name.contains("壁纸")) {
            return "wall";
        }
        return "vod";
    }

    public boolean onBackPressed() {
        if (!isAdded()) return false;
        if (fullscreenEditorContainer != null && fullscreenEditorContainer.getVisibility() == View.VISIBLE) {
            closeFullscreenEditor();
            return true;
        }
        if (!backStack.isEmpty()) {
            goBack();
            return true;
        }
        return false;
    }
}
