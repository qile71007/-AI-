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
import android.util.Log;
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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import okhttp3.Call;
import okhttp3.Response;

public class FileManagerFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvPath;
    private ImageButton btnBackDir, btnForwardDir, btnNewFolder;
    private LinearLayout buttonContainer;
    private ImageButton btnEdit, btnPackage;
    private FileAdapter adapter;
    private File currentDir;
    private List<File> fileList = new ArrayList<>();
    private File copiedFile = null;
    private boolean isAttached = false;

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

    private List<Integer> matchPositions = new ArrayList<>();
    private int currentMatchIndex = -1;
    private String lastSearchKeyword = null;
    private boolean isPerformingSearch = false;

    private static final int MAX_DRAG_SETUP_RETRIES = 10;
    private int dragSetupRetryCount = 0;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) loadDirectory(Environment.getExternalStorageDirectory(), false);
                        else Toast.makeText(getContext(), "需要存储权限才能访问文件", Toast.LENGTH_SHORT).show();
                    });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (Environment.isExternalStorageManager())
                                loadDirectory(Environment.getExternalStorageDirectory(), false);
                            else Toast.makeText(getContext(), "需要授予「所有文件访问权限」", Toast.LENGTH_SHORT).show();
                        }
                    });

    public static FileManagerFragment newInstance() { return new FileManagerFragment(); }

    @Override
    public void onAttach(@NonNull Context context) { super.onAttach(context); isAttached = true; }
    @Override
    public void onDetach() { super.onDetach(); isAttached = false; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_manager, container, false);
        recyclerView = Objects.requireNonNull(view.findViewById(R.id.recyclerView));
        tvPath = Objects.requireNonNull(view.findViewById(R.id.tv_path));
        btnBackDir = Objects.requireNonNull(view.findViewById(R.id.btn_back_dir));
        btnForwardDir = Objects.requireNonNull(view.findViewById(R.id.btn_forward_dir));
        btnNewFolder = Objects.requireNonNull(view.findViewById(R.id.btn_new_folder));
        buttonContainer = Objects.requireNonNull(view.findViewById(R.id.button_container));
        btnEdit = Objects.requireNonNull(view.findViewById(R.id.btn_edit));
        btnPackage = Objects.requireNonNull(view.findViewById(R.id.btn_package));
        fullscreenEditorContainer = Objects.requireNonNull(view.findViewById(R.id.fullscreen_editor_container));

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        btnBackDir.setOnClickListener(v -> goBack());
        btnForwardDir.setOnClickListener(v -> goForward());
        btnNewFolder.setOnClickListener(v -> newFolder());
        btnEdit.setOnClickListener(v -> editCurrentConfig());
        btnPackage.setOnClickListener(v -> packageCurrentConfig());

        setupDragButton();
        setupFullscreenEditor();
        detectCurrentConfig();
        checkPermissionsAndLoad();
        return view;
    }

    // ==================== 本地化打包增强版 ====================
    private void packageCurrentConfig() {
        if (!isAdded()) return;
        detectCurrentConfig();
        if (TextUtils.isEmpty(currentConfigType) || TextUtils.isEmpty(currentConfigUrl)) {
            Toast.makeText(getContext(), "未检测到当前配置", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "开始生成本地化包，请稍候...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            // 获取安全的 Context 引用
            Context appCtx = getContext() != null ? getContext().getApplicationContext() : null;
            if (appCtx == null) return;

            try {
                // 1. 获取配置内容与基础信息
                String configContent;
                File configBaseDir = null;
                String baseUrl = null; // 用于远程配置的相对路径拼接
                File tmpDir = new File(appCtx.getCacheDir(), "pkg_" + System.currentTimeMillis());
                if (!tmpDir.mkdirs()) throw new IOException("无法创建临时目录");

                if (isRemoteConfig) {
                    // 下载远程配置
                    okhttp3.Request req = new okhttp3.Request.Builder().url(currentConfigUrl).build();
                    try (Response resp = OkHttp.get().newCall(req).execute()) {
                        if (!resp.isSuccessful()) throw new IOException("下载配置失败: " + resp.code());
                        configContent = resp.body() != null ? resp.body().string() : "";
                    }
                    // 将配置内容保存到临时目录（用于后续打包）
                    File tmpConfig = new File(tmpDir, getConfigFileName());
                    try (FileWriter fw = new FileWriter(tmpConfig)) { fw.write(configContent); }
                    configBaseDir = tmpDir; // 相对路径解析的基础目录（但远程情况下，我们使用 baseUrl 构建 URL）
                    baseUrl = currentConfigUrl; // 用于拼接相对路径
                } else {
                    if (currentConfigFile == null || !currentConfigFile.exists()) throw new IOException("本地配置文件不存在");
                    configContent = readFileToString(currentConfigFile);
                    configBaseDir = currentConfigFile.getParentFile();
                    baseUrl = null;
                }

                // 2. 解析所有资源引用（URL、绝对路径、相对路径）
                Map<String, String> sourceToAbsolute = parseAllLinks(configContent, configBaseDir, baseUrl);
                // 移除配置自身的引用（避免重复打包）
                if (currentConfigFile != null) sourceToAbsolute.remove(currentConfigFile.getAbsolutePath());
                sourceToAbsolute.remove(currentConfigUrl);

                // 3. 下载/复制资源到临时目录，并建立原始引用 → 本地相对路径 的映射
                Map<String, String> sourceToLocalPath = new ConcurrentHashMap<>();
                CountDownLatch latch = new CountDownLatch(sourceToAbsolute.size());
                List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

                for (Map.Entry<String, String> entry : sourceToAbsolute.entrySet()) {
                    String originalRef = entry.getKey();      // 配置中原样引用的字符串
                    String absPath = entry.getValue();        // 解析后的绝对路径或 URL
                    if (absPath.startsWith("http")) {
                        downloadFileAsync(originalRef, absPath, tmpDir, sourceToLocalPath, latch, errors);
                    } else {
                        copyLocalFile(originalRef, new File(absPath), tmpDir, configBaseDir, sourceToLocalPath, latch, errors);
                    }
                }

                // 等待所有任务完成（最多 60 秒）
                latch.await(60, TimeUnit.SECONDS);
                if (!errors.isEmpty()) {
                    Log.w("Package", "部分资源处理失败: " + errors.size());
                }

                // 4. 重写配置文件内容，将原始引用替换为本地相对路径
                String rewrittenContent = rewriteConfigContent(configContent, sourceToLocalPath);
                // 覆盖临时目录中的配置文件
                try (FileWriter fw = new FileWriter(new File(tmpDir, getConfigFileName()))) {
                    fw.write(rewrittenContent);
                }

                // 5. 打包 ZIP
                File zipFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        "local_" + currentConfigType + "_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".zip");
                createZipFromDir(tmpDir, zipFile);
                deleteRecursive(tmpDir);

                // 提示成功（必须在 UI 线程）
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "本地化包已保存至:\n" + zipFile.getAbsolutePath(), Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            Toast.makeText(getContext(), "打包失败: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        }).start();
    }

    /**
     * 解析配置内容中的所有资源引用，返回 Map<原始引用, 绝对路径/URL>
     * @param content 配置内容
     * @param baseDir 本地配置文件所在目录（为 null 表示远程）
     * @param baseUrl 远程配置的基础 URL（为 null 表示本地）
     */
    private Map<String, String> parseAllLinks(String content, File baseDir, String baseUrl) {
        Map<String, String> result = new HashMap<>();
        // 匹配 URL
        Pattern urlPat = Pattern.compile("https?://[^\\s\"'<>]+");
        Matcher m = urlPat.matcher(content);
        while (m.find()) {
            String url = m.group();
            result.put(url, url);
        }

        // 匹配路径（非 URL）：支持 /absolute, ./rel, ../rel, 或纯文件名
        Pattern pathPat = Pattern.compile("(?<!https?://)(?:/\\S+|(?<=\\s|\"|')(?:\\.{0,2}/\\S+)|(?<=\\s|\"|')(?:[^/\\s]+\\.\\w+))");
        m = pathPat.matcher(content);
        while (m.find()) {
            String path = m.group().trim();
            if (path.isEmpty() || path.startsWith("http")) continue;

            if (path.startsWith("/")) {
                // 绝对路径 – 仅当 baseDir 存在且文件存在时处理（本地）
                if (baseDir != null) {
                    File resolved = new File(path);
                    if (resolved.exists()) {
                        result.put(path, resolved.getAbsolutePath());
                    }
                }
            } else {
                // 相对路径
                if (baseDir != null) {
                    // 本地文件 – 基于 baseDir 解析
                    try {
                        File resolved = new File(baseDir, path).getCanonicalFile();
                        if (resolved.exists()) {
                            result.put(path, resolved.getAbsolutePath());
                        }
                    } catch (IOException ignored) {}
                } else if (baseUrl != null) {
                    // 远程配置 – 基于 baseUrl 拼接成完整 URL
                    try {
                        URI baseUri = new URI(baseUrl);
                        URI resolvedUri = baseUri.resolve(path);
                        String fullUrl = resolvedUri.toString();
                        // 不检查存在性，直接添加到下载列表
                        result.put(path, fullUrl);
                    } catch (URISyntaxException ignored) {}
                }
            }
        }
        return result;
    }

    /**
     * 下载远程资源，并记录映射（保留目录结构）
     */
    private void downloadFileAsync(String originalRef, String url, File destDir,
                                   Map<String, String> sourceToLocalPath,
                                   CountDownLatch latch, List<Exception> errors) {
        new Thread(() -> {
            try {
                // 从 URL 提取路径，保留子目录结构
                Uri uri = Uri.parse(url);
                String path = uri.getPath();
                String fileName = (path != null && !path.isEmpty()) ? path.substring(path.lastIndexOf('/') + 1) : "file";
                if (TextUtils.isEmpty(fileName)) fileName = "f_" + System.currentTimeMillis();
                String host = uri.getHost();
                String dirPath = "";
                if (host != null) {
                    dirPath = host + (path != null ? path.substring(0, path.lastIndexOf('/') + 1) : "/");
                }
                File targetDir = new File(destDir, dirPath);
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    throw new IOException("无法创建目录: " + targetDir);
                }
                File out = new File(targetDir, fileName);
                // 重名处理
                if (out.exists()) {
                    String base = fileName.replaceFirst("[.][^.]+$", "");
                    String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : "";
                    out = new File(targetDir, base + "_" + System.currentTimeMillis() + ext);
                }
                // 下载
                okhttp3.Request req = new okhttp3.Request.Builder().url(url).build();
                try (Response resp = OkHttp.get().newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) {
                        throw new IOException("HTTP " + (resp.code()));
                    }
                    try (InputStream is = resp.body().byteStream(); OutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
                    }
                }
                // 记录映射：原始引用 → 本地相对路径（相对于 destDir）
                String relative = destDir.toURI().relativize(out.toURI()).getPath();
                sourceToLocalPath.put(originalRef, "./" + relative);
            } catch (Exception e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        }).start();
    }

    /**
     * 复制本地文件，并记录映射（保留相对路径结构）
     */
    private void copyLocalFile(String originalRef, File srcFile, File destDir, File baseDir,
                               Map<String, String> sourceToLocalPath,
                               CountDownLatch latch, List<Exception> errors) {
        new Thread(() -> {
            try {
                // 保持相对路径（相对于 baseDir）
                String relativePath = baseDir.toURI().relativize(srcFile.toURI()).getPath();
                File dest = new File(destDir, relativePath);
                File parent = dest.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                // 重名处理
                if (dest.exists()) {
                    String name = dest.getName();
                    String base = name.replaceFirst("[.][^.]+$", "");
                    String ext = name.contains(".") ? name.substring(name.lastIndexOf(".")) : "";
                    dest = new File(dest.getParent(), base + "_" + System.currentTimeMillis() + ext);
                }
                try (InputStream is = new FileInputStream(srcFile); OutputStream os = new FileOutputStream(dest)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
                }
                // 记录映射
                String relative = destDir.toURI().relativize(dest.toURI()).getPath();
                sourceToLocalPath.put(originalRef, "./" + relative);
            } catch (Exception e) {
                errors.add(e);
            } finally {
                latch.countDown();
            }
        }).start();
    }

    /**
     * 重写配置内容：将所有原始引用替换为本地相对路径
     */
    private String rewriteConfigContent(String content, Map<String, String> sourceToLocalPath) {
        String rewritten = content;
        for (Map.Entry<String, String> entry : sourceToLocalPath.entrySet()) {
            String original = entry.getKey();
            String local = entry.getValue();
            // 普通字符串替换（配置通常为 JSON/M3U，简单替换足够）
            rewritten = rewritten.replace(original, local);
        }
        return rewritten;
    }

    // ==================== 原有辅助方法（保持不变） ====================
    private String readFileToString(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        return sb.toString();
    }

    private String getConfigFileName() {
        switch (currentConfigType) {
            case "vod": return "vod_config.json";
            case "live": return "live_config.m3u";
            case "wall": return "wall_config.json";
            default: return "config.txt";
        }
    }

    private void createZipFromDir(File srcDir, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile); ZipOutputStream zos = new ZipOutputStream(fos)) {
            zipDir(srcDir, srcDir, zos);
        }
    }

    private void zipDir(File rootDir, File currentDir, ZipOutputStream zos) throws IOException {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) zipDir(rootDir, file, zos);
            else {
                String entry = rootDir.toURI().relativize(file.toURI()).getPath();
                try (FileInputStream fis = new FileInputStream(file); BufferedInputStream bis = new BufferedInputStream(fis)) {
                    zos.putNextEntry(new ZipEntry(entry));
                    byte[] buf = new byte[4096]; int len;
                    while ((len = bis.read(buf)) != -1) zos.write(buf, 0, len);
                    zos.closeEntry();
                }
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        file.delete();
    }

    // ==================== 搜索相关（保持不变） ====================
    private void setupFullscreenEditor() {
        if (fullscreenEditorContainer == null) return;
        fullscreenEditor = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.fullscreen_editor));
        fullscreenTitle = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.fullscreen_title));
        btnCloseEditor = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_close_editor));
        btnSaveEditor = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_save_editor));
        btnSearchToggle = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_editor));
        btnJump = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_jump_editor));
        searchLayout = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.search_layout));
        searchEditText = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.search_edit_text));
        searchCount = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.search_count));
        btnSearchPrev = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_prev));
        btnSearchNext = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_next));
        btnSearchClose = Objects.requireNonNull(fullscreenEditorContainer.findViewById(R.id.btn_search_close));

        fullscreenEditor.setTypeface(android.graphics.Typeface.MONOSPACE);
        fullscreenEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isPerformingSearch) return;
                clearHighlights();
                matchPositions.clear();
                currentMatchIndex = -1;
                updateSearchCount();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

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

        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString(), false);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                performSearch(searchEditText.getText().toString(), true);
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchEditText.getWindowToken(), 0);
                return true;
            }
            return false;
        });
    }

    private void toggleSearchBar() {
        if (searchLayout.getVisibility() == View.VISIBLE) closeSearchBar();
        else {
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

    private void performSearch(String keyword, boolean scrollToFirst) {
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
        updateSearchCount();
        if (scrollToFirst) scrollToMatch(0);
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
        for (BackgroundColorSpan span : spans) editable.removeSpan(span);
    }

    private void updateSearchCount() {
        int total = matchPositions.size();
        if (total > 0 && currentMatchIndex >= 0 && currentMatchIndex < total)
            searchCount.setText((currentMatchIndex + 1) + "/" + total);
        else searchCount.setText(total > 0 ? "0/" + total : "");
        btnSearchPrev.setEnabled(total > 1);
        btnSearchNext.setEnabled(total > 1);
    }

    private void showJumpDialog() {
        if (fullscreenEditor == null || fullscreenEditor.getLayout() == null) {
            fullscreenEditor.post(this::showJumpDialog);
            return;
        }
        int totalLines = fullscreenEditor.getLineCount();
        if (totalLines == 0) { Toast.makeText(getContext(), "文档为空", Toast.LENGTH_SHORT).show(); return; }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("跳转到行号 (1-" + totalLines + ")");
        final EditText input = new EditText(requireContext());
        input.setHint("输入行号 (1-" + totalLines + ")");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        builder.setView(input);
        builder.setPositiveButton("跳转", (d, w) -> {
            try {
                int line = Integer.parseInt(input.getText().toString().trim());
                if (line < 1 || line > totalLines) throw new NumberFormatException();
                jumpToLine(line);
            } catch (Exception e) { Toast.makeText(getContext(), "行号无效", Toast.LENGTH_SHORT).show(); }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void jumpToLine(int lineNumber) {
        Layout layout = fullscreenEditor.getLayout();
        if (layout == null || lineNumber < 1 || lineNumber > layout.getLineCount()) return;
        int start = layout.getLineStart(lineNumber - 1);
        fullscreenEditor.setSelection(start);
        fullscreenEditor.requestFocus();
        int y = layout.getLineTop(lineNumber - 1);
        fullscreenEditor.scrollTo(0, Math.max(0, y - 20));
    }

    // ==================== 编辑器及文件操作 ====================
    private void showFullscreenEditor(String content, String fileName) {
        if (fullscreenEditorContainer == null) return;
        String title = "编辑: " + fileName + (isRemoteConfig ? " (远程)" : "");
        fullscreenTitle.setText(title);
        fullscreenEditor.setText(content);
        fullscreenEditor.post(() -> { fullscreenEditor.setSelection(0); fullscreenEditor.scrollTo(0, 0); });
        fullscreenEditorContainer.setVisibility(View.VISIBLE);
        fullscreenEditorContainer.bringToFront();
        fullscreenEditor.requestFocus();
        if (getActivity() != null) getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
    }

    private void closeFullscreenEditor() {
        if (fullscreenEditorContainer != null) fullscreenEditorContainer.setVisibility(View.GONE);
        closeSearchBar();
        if (getActivity() != null) getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
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
                if (dragSetupRetryCount < MAX_DRAG_SETUP_RETRIES) { dragSetupRetryCount++; buttonContainer.post(this::setupDragButton); }
                else dragSetupRetryCount = 0;
                return;
            }
            dragSetupRetryCount = 0;
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
        buttonContainer.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY, lastX, lastY; boolean isDragging;
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lastX = event.getRawX(); startY = lastY = event.getRawY(); isDragging = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - lastX, dy = event.getRawY() - lastY;
                        if (Math.abs(event.getRawX() - startX) > 10 || Math.abs(event.getRawY() - startY) > 10) isDragging = true;
                        if (isDragging) {
                            RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) v.getLayoutParams();
                            int newLeft = lp.leftMargin + (int) dx, newTop = lp.topMargin + (int) dy;
                            View p = (View) v.getParent();
                            if (p == null) return true;
                            int maxX = p.getWidth() - v.getWidth(), maxY = p.getHeight() - v.getHeight() - getNavBarHeight() - dpToPx(8);
                            lp.leftMargin = Math.max(0, Math.min(newLeft, maxX));
                            lp.topMargin = Math.max(0, Math.min(newTop, maxY));
                            v.setLayoutParams(lp);
                            lastX = event.getRawX(); lastY = event.getRawY();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) btnEdit.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private int getNavBarHeight() {
        if (!isAttached || getActivity() == null) return dpToPx(56);
        View nav = getActivity().findViewById(R.id.navigation);
        return nav != null ? nav.getHeight() : dpToPx(56);
    }

    private int dpToPx(int dp) { return (int) (dp * requireContext().getResources().getDisplayMetrics().density + 0.5f); }

    private void detectCurrentConfig() {
        try {
            String url = VodConfig.get().getUrl();
            if (!TextUtils.isEmpty(url)) { setConfigInfo(url, "vod"); return; }
            url = LiveConfig.get().getUrl();
            if (!TextUtils.isEmpty(url)) { setConfigInfo(url, "live"); return; }
            url = WallConfig.get().getUrl();
            if (!TextUtils.isEmpty(url)) { setConfigInfo(url, "wall"); }
        } catch (Exception ignored) {}
    }

    private void setConfigInfo(String url, String type) {
        currentConfigUrl = url; currentConfigType = type;
        if (url.startsWith("file://") || url.startsWith("/")) {
            currentConfigFile = new File(url.replace("file://", ""));
            isRemoteConfig = false;
        } else { isRemoteConfig = true; currentConfigFile = null; }
    }

    private void editCurrentConfig() {
        if (fullscreenEditorContainer != null && fullscreenEditorContainer.getVisibility() == View.VISIBLE) return;
        detectCurrentConfig();
        if (!TextUtils.isEmpty(currentConfigType) && !TextUtils.isEmpty(currentConfigUrl)) {
            if (isRemoteConfig) { downloadAndEditRemoteConfig(); return; }
            else if (currentConfigFile != null && currentConfigFile.exists()) { readAndEditLocalFile(currentConfigFile); return; }
            else if (currentConfigUrl.startsWith("http")) { downloadAndEditRemoteConfig(); return; }
            else Toast.makeText(getContext(), "本地配置文件不存在", Toast.LENGTH_LONG).show();
            return;
        }
        showFilePickerForEdit();
    }

    private void downloadAndEditRemoteConfig() {
        if (TextUtils.isEmpty(currentConfigUrl) || (!currentConfigUrl.startsWith("http"))) {
            Toast.makeText(getContext(), "无效的网络地址", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "正在下载远程配置...", Toast.LENGTH_SHORT).show();
        File targetFile = getDownloadConfigFile(currentConfigType, currentConfigUrl);
        OkHttp.get().newCall(currentConfigUrl).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (isAttached) requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String content = response.body().string();
                    if (isAttached) requireActivity().runOnUiThread(() -> {
                        try {
                            try (FileWriter writer = new FileWriter(targetFile)) { writer.write(content); writer.flush(); }
                            currentConfigFile = targetFile; isRemoteConfig = false; currentConfigUrl = "file://" + targetFile.getAbsolutePath();
                            Toast.makeText(getContext(), "下载成功", Toast.LENGTH_SHORT).show();
                            showFullscreenEditor(content, targetFile.getName());
                        } catch (IOException e) { Toast.makeText(getContext(), "保存失败", Toast.LENGTH_SHORT).show(); }
                    });
                } else if (isAttached) requireActivity().runOnUiThread(() -> Toast.makeText(getContext(), "下载失败: HTTP " + response.code(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void readAndEditLocalFile(File file) {
        if (!file.exists() || !file.canRead()) { Toast.makeText(getContext(), "文件不存在或无法读取", Toast.LENGTH_SHORT).show(); return; }
        if (file.length() > 5 * 1024 * 1024) { Toast.makeText(getContext(), "文件过大（超过5MB）", Toast.LENGTH_LONG).show(); return; }
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) content.append(line).append("\n");
        } catch (IOException e) { Toast.makeText(getContext(), "读取文件失败", Toast.LENGTH_SHORT).show(); return; }
        showFullscreenEditor(content.toString(), file.getName());
    }

    private void saveAndApplyConfig(String content) {
        if (currentConfigFile == null) { Toast.makeText(getContext(), "未选择配置文件", Toast.LENGTH_SHORT).show(); return; }
        try (FileWriter writer = new FileWriter(currentConfigFile)) {
            writer.write(content); writer.flush();
            Toast.makeText(getContext(), "保存成功", Toast.LENGTH_LONG).show();
            if (isRemoteConfig) isRemoteConfig = false;
            applyConfig(currentConfigFile.getAbsolutePath());
            loadDirectory(currentDir, true);
        } catch (IOException e) { Toast.makeText(getContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
    }

    private void applyConfig(String filePath) {
        File configFile = new File(filePath);
        if (!configFile.exists() || !configFile.canRead()) { Toast.makeText(getContext(), "配置文件不可读", Toast.LENGTH_LONG).show(); return; }
        try {
            String content = readFileFirstLines(configFile, 10);
            if (TextUtils.isEmpty(content)) { Toast.makeText(getContext(), "配置文件为空", Toast.LENGTH_LONG).show(); return; }
            String url = "file://" + filePath;
            int type;
            switch (currentConfigType) {
                case "vod": type = 0; break;
                case "live": type = 1; break;
                case "wall": type = 2; break;
                default: return;
            }
            Config config = Config.find(url, type);
            if (config == null) { Toast.makeText(getContext(), "无法创建配置记录", Toast.LENGTH_LONG).show(); return; }
            switch (currentConfigType) {
                case "vod": VodConfig.get().load(config, new Callback() {
                    @Override public void success() { if (isAdded()) { Notify.show("点播配置加载成功"); EventBus.getDefault().post(new ConfigEvent(ConfigEvent.Type.COMMON)); RefreshEvent.home(); } }
                    @Override public void error(String msg) { if (isAdded()) Notify.show("点播配置加载失败: " + msg); }
                }); break;
                case "live": LiveConfig.get().load(config, new Callback() {
                    @Override public void success() { if (isAdded()) { Notify.show("直播配置加载成功"); EventBus.getDefault().post(new ConfigEvent(ConfigEvent.Type.COMMON)); RefreshEvent.home(); } }
                    @Override public void error(String msg) { if (isAdded()) Notify.show("直播配置加载失败: " + msg); }
                }); break;
                case "wall": WallConfig.get().load(config, new Callback() {
                    @Override public void success() { if (isAdded()) { Notify.show("壁纸配置加载成功"); EventBus.getDefault().post(new ConfigEvent(ConfigEvent.Type.COMMON)); RefreshEvent.home(); } }
                    @Override public void error(String msg) { if (isAdded()) Notify.show("壁纸配置加载失败: " + msg); }
                }); break;
            }
            detectCurrentConfig();
        } catch (Exception e) { Toast.makeText(getContext(), "应用配置异常: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private File getDownloadConfigFile(String type, String url) {
        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!dir.exists()) dir.mkdirs();
        String name = type + "_" + System.currentTimeMillis() + ".txt";
        return new File(dir, name);
    }

    private void setCurrentConfig(File configFile, String type) {
        if (!isAdded()) return;
        if (configFile == null || !configFile.exists()) { Toast.makeText(getContext(), "文件不存在", Toast.LENGTH_SHORT).show(); return; }
        currentConfigFile = configFile; currentConfigType = type; isRemoteConfig = false;
        currentConfigUrl = "file://" + configFile.getAbsolutePath();
        Toast.makeText(getContext(), "正在加载配置...", Toast.LENGTH_SHORT).show();
        applyConfig(configFile.getAbsolutePath());
    }

    private String readFileFirstLines(File file, int lines) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line; int i = 0;
            while ((line = br.readLine()) != null && i < lines) { sb.append(line).append("\n"); i++; }
        } catch (IOException ignored) {}
        return sb.toString();
    }

    // ==================== 文件列表及操作 ====================
    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                manageStorageLauncher.launch(intent);
            } else loadDirectory(Environment.getExternalStorageDirectory(), false);
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            else loadDirectory(Environment.getExternalStorageDirectory(), false);
        }
    }

    private void loadDirectory(File dir) { loadDirectory(dir, true); }

    private void loadDirectory(File dir, boolean addToBackStack) {
        if (dir == null || !dir.exists()) { Toast.makeText(getContext(), "目录不存在", Toast.LENGTH_SHORT).show(); return; }
        if (addToBackStack) { if (currentDir != null) backStack.push(currentDir); forwardStack.clear(); }
        currentDir = dir; tvPath.setText(dir.getAbsolutePath()); fileList.clear();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) if (!f.getName().startsWith(".")) fileList.add(f);
            Collections.sort(fileList, (o1, o2) -> {
                if (o1.isDirectory() && !o2.isDirectory()) return -1;
                if (!o1.isDirectory() && o2.isDirectory()) return 1;
                return o1.getName().compareToIgnoreCase(o2.getName());
            });
        }
        adapter.notifyDataSetChanged(); updateNavigationButtons();
    }

    private void updateNavigationButtons() {
        if (btnBackDir != null) { btnBackDir.setEnabled(!backStack.isEmpty()); btnBackDir.setAlpha(backStack.isEmpty() ? 0.4f : 1.0f); }
        if (btnForwardDir != null) { btnForwardDir.setEnabled(!forwardStack.isEmpty()); btnForwardDir.setAlpha(forwardStack.isEmpty() ? 0.4f : 1.0f); }
    }

    private void goBack() { if (isAdded() && !backStack.isEmpty()) { forwardStack.push(currentDir); loadDirectory(backStack.pop(), false); } }
    private void goForward() { if (isAdded() && !forwardStack.isEmpty()) { backStack.push(currentDir); loadDirectory(forwardStack.pop(), false); } }

    private void deleteFile(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteFile(child);
        }
        file.delete();
        loadDirectory(currentDir);
    }

    private void renameFile(File file) {
        EditText input = new EditText(getContext());
        input.setText(file.getName());
        new AlertDialog.Builder(getContext())
                .setTitle("重命名").setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show(); return; }
                    if (file.renameTo(new File(file.getParent(), name))) loadDirectory(currentDir);
                    else Toast.makeText(getContext(), "重命名失败", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void newFolder() {
        EditText input = new EditText(getContext());
        input.setHint("文件夹名称");
        new AlertDialog.Builder(getContext())
                .setTitle("新建文件夹").setView(input)
                .setPositiveButton("确定", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) { Toast.makeText(getContext(), "名称不能为空", Toast.LENGTH_SHORT).show(); return; }
                    if (new File(currentDir, name).mkdirs()) loadDirectory(currentDir);
                    else Toast.makeText(getContext(), "创建失败", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void copyFile(File src) { copiedFile = src; Toast.makeText(getContext(), "已复制", Toast.LENGTH_SHORT).show(); }

    private void pasteFile() {
        if (copiedFile == null || !copiedFile.exists()) { Toast.makeText(getContext(), "无可粘贴文件", Toast.LENGTH_SHORT).show(); return; }
        if (copiedFile.getParent().equals(currentDir.getAbsolutePath())) { Toast.makeText(getContext(), "目标与源相同", Toast.LENGTH_SHORT).show(); return; }
        if (copiedFile.isFile()) {
            File dest = new File(currentDir, copiedFile.getName());
            int i = 1;
            while (dest.exists()) {
                String name = copiedFile.getName();
                int dot = name.lastIndexOf('.');
                String base = (dot == -1) ? name : name.substring(0, dot);
                String ext = (dot == -1) ? "" : name.substring(dot);
                dest = new File(currentDir, base + "(" + i + ")" + ext);
                i++;
            }
            try (InputStream in = new FileInputStream(copiedFile); OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[4096]; int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                Toast.makeText(getContext(), "粘贴成功", Toast.LENGTH_SHORT).show();
                loadDirectory(currentDir);
            } catch (Exception e) { Toast.makeText(getContext(), "粘贴失败", Toast.LENGTH_SHORT).show(); }
        } else Toast.makeText(getContext(), "暂不支持文件夹", Toast.LENGTH_SHORT).show();
    }

    private void editTextFile(File file) {
        if (fullscreenEditorContainer != null && fullscreenEditorContainer.getVisibility() == View.VISIBLE) return;
        if (!file.canRead()) { Toast.makeText(getContext(), "无法读取", Toast.LENGTH_SHORT).show(); return; }
        if (file.length() > 5 * 1024 * 1024) { Toast.makeText(getContext(), "文件过大（超过5MB）", Toast.LENGTH_LONG).show(); return; }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        } catch (IOException e) { Toast.makeText(getContext(), "读取失败", Toast.LENGTH_SHORT).show(); return; }
        currentConfigFile = file; currentConfigType = detectConfigType(file); isRemoteConfig = false;
        currentConfigUrl = "file://" + file.getAbsolutePath();
        showFullscreenEditor(sb.toString(), file.getName());
    }

    private void showFilePickerForEdit() {
        List<File> files = new ArrayList<>();
        if (currentDir != null && currentDir.listFiles() != null)
            for (File f : currentDir.listFiles()) if (f.isFile() && !f.getName().startsWith(".")) files.add(f);
        if (files.isEmpty()) { Toast.makeText(getContext(), "无可编辑文件", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) names[i] = files.get(i).getName();
        new AlertDialog.Builder(getContext()).setTitle("选择文件").setItems(names, (d, i) -> editTextFile(files.get(i))).setNegativeButton("取消", null).show();
    }

    // ==================== 文件类型判断 ====================
    private boolean isVideoFile(String n) { n = n.toLowerCase(); return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".avi") || n.endsWith(".mov"); }
    private boolean isAudioFile(String n) { n = n.toLowerCase(); return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac"); }
    private boolean isImageFile(String n) { n = n.toLowerCase(); return n.endsWith(".jpg") || n.endsWith(".png") || n.endsWith(".bmp"); }
    private boolean isTextFile(String n) { n = n.toLowerCase(); return n.endsWith(".txt") || n.endsWith(".json") || n.endsWith(".xml") || n.endsWith(".m3u"); }

    private void playVideo(File f) { if (getActivity() != null) VideoActivity.file(getActivity(), f.getAbsolutePath()); }
    private void playAudio(File f) { if (getActivity() != null) VideoActivity.file(getActivity(), f.getAbsolutePath()); }
    private void previewImage(File f) {
        if (getActivity() == null) return;
        startActivity(new Intent(getActivity(), ImagePreviewActivity.class).putExtra("image_path", f.getAbsolutePath()));
    }

    // ==================== Adapter ====================
    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvInfo; ImageButton ivIcon;
            VH(View v) { super(v);
                tvName = Objects.requireNonNull(v.findViewById(R.id.tv_name));
                tvInfo = Objects.requireNonNull(v.findViewById(R.id.tv_info));
                ivIcon = Objects.requireNonNull(v.findViewById(R.id.iv_icon));
            }
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) { return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_file, p, false)); }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            File f = fileList.get(pos);
            h.tvName.setText(f.getName());
            h.tvInfo.setText(f.isDirectory() ? "文件夹" : (formatSize(f.length()) + "  " + formatTime(f.lastModified())));
            h.ivIcon.setImageResource(f.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
            h.itemView.setOnClickListener(v -> {
                if (f.isDirectory()) loadDirectory(f);
                else { String n = f.getName();
                    if (isVideoFile(n)) playVideo(f); else if (isAudioFile(n)) playAudio(f); else if (isImageFile(n)) previewImage(f); else if (isTextFile(n)) editTextFile(f);
                    else Toast.makeText(getContext(), "不支持此类型", Toast.LENGTH_SHORT).show(); }
            });
            h.itemView.setOnLongClickListener(v -> { (f.isDirectory() ? showFolderOptions(f) : showFileOptions(f)); return true; });
        }
        @Override public int getItemCount() { return fileList.size(); }
        private String formatSize(long s) {
            if (s < 1024) return s + " B";
            int z = (63 - Long.numberOfLeadingZeros(s)) / 10;
            return String.format(Locale.US, "%.1f %sB", (double) s / (1L << (z * 10)), " KMGTPE".charAt(z));
        }
        private String formatTime(long t) { return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(t)); }
    }

    private void showFileOptions(File file) {
        new AlertDialog.Builder(getContext()).setTitle(file.getName()).setItems(new String[]{"删除","重命名","复制","剪切","粘贴","设为当前配置","编辑","播放/查看"}, (d, i) -> {
            switch (i) {
                case 0: deleteFile(file); break; case 1: renameFile(file); break; case 2: copyFile(file); break;
                case 3: copyFile(file); deleteFile(file); break; case 4: pasteFile(); break;
                case 5: setCurrentConfig(file, detectConfigType(file)); break;
                case 6: if (!file.isDirectory()) editTextFile(file); break;
                case 7: if (!file.isDirectory()) { String n = file.getName(); if (isVideoFile(n) || isAudioFile(n)) playVideo(file); else if (isImageFile(n)) previewImage(file); else Toast.makeText(getContext(), "不支持", Toast.LENGTH_SHORT).show(); }
            }
        }).setNegativeButton("取消", null).show();
    }

    private void showFolderOptions(File file) {
        new AlertDialog.Builder(getContext()).setTitle(file.getName()).setItems(new String[]{"删除","重命名","复制","剪切","粘贴"}, (d, i) -> {
            switch (i) {
                case 0: deleteFile(file); break; case 1: renameFile(file); break; case 2: copyFile(file); break;
                case 3: copyFile(file); deleteFile(file); break; case 4: pasteFile(); break;
            }
        }).setNegativeButton("取消", null).show();
    }

    private String detectConfigType(File file) {
        String n = file.getName().toLowerCase();
        if (n.contains("vod") || n.contains("点播") || n.endsWith(".json")) return "vod";
        if (n.contains("live") || n.contains("直播") || n.endsWith(".m3u")) return "live";
        if (n.contains("wall") || n.contains("壁纸")) return "wall";
        return "vod";
    }

    public boolean onBackPressed() {
        if (!isAdded()) return false;
        if (fullscreenEditorContainer != null && fullscreenEditorContainer.getVisibility() == View.VISIBLE) { closeFullscreenEditor(); return true; }
        if (!backStack.isEmpty()) { goBack(); return true; }
        return false;
    }
}
