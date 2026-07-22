package com.fongmi.android.tv.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFileBinding;
import com.fongmi.android.tv.ui.adapter.FileAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class FileActivity extends BaseActivity implements FileAdapter.OnClickListener {

    private ActivityFileBinding mBinding;
    private FileAdapter mAdapter;
    private File dir;
    private boolean selectDir;

    // 路径导航
    private TextView tvPath;
    private ImageButton btnBack;

    // 编辑器
    private LinearLayout editorContainer;
    private EditText editorText;
    private ImageButton btnCloseEditor;
    private ImageButton btnTts;
    private ImageButton btnSave;
    private TextView editorTitle;
    private File editingFile;

    // TTS
    private TextToSpeech ttsEngine;
    private boolean isSpeaking = false;
    private boolean ttsInitialized = false;

    private boolean isRoot() {
        return Path.root().equals(dir);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFileBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        selectDir = getIntent().getBooleanExtra("select_dir", false);
        initViews();
        setRecyclerView();
        checkPermission();
    }

    private void initViews() {
        tvPath = mBinding.tvPath;
        btnBack = mBinding.btnBack;
        editorContainer = mBinding.editorContainer;
        editorText = mBinding.editorText;
        btnCloseEditor = mBinding.btnCloseEditor;
        btnTts = mBinding.btnTts;
        btnSave = mBinding.btnSave;
        editorTitle = mBinding.editorTitle;

        btnBack.setOnClickListener(v -> {
            if (editorContainer.getVisibility() == View.VISIBLE) {
                closeEditor();
            } else if (!isRoot()) {
                update(dir.getParentFile());
            } else {
                finish();
            }
        });
        btnCloseEditor.setOnClickListener(v -> closeEditor());
        btnSave.setOnClickListener(v -> saveEditor());
        btnTts.setOnClickListener(v -> toggleTts());
    }

    private void setRecyclerView() {
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(4));
        mBinding.recycler.setAdapter(mAdapter = new FileAdapter(this));
    }

    private void checkPermission() {
        PermissionUtil.requestFile(this, allGranted -> update(Path.root()));
    }

    private void update(File dir) {
        mBinding.recycler.setSelectedPosition(0);
        this.dir = dir;
        if (tvPath != null) tvPath.setText(dir.getAbsolutePath());
        mAdapter.addAll(dir, list(dir), selectDir);
        mBinding.progressLayout.showContent(true, mAdapter.getItemCount());
    }

    private List<File> list(File dir) {
        if (!selectDir) return Path.list(dir);
        File[] files = dir.listFiles(File::isDirectory);
        if (files == null) return new ArrayList<>();
        Path.sort(files);
        return Arrays.asList(files);
    }

    @Override
    public void onItemClick(File file) {
        if (file.isDirectory()) {
            update(file);
        } else if (isTextFile(file)) {
            openEditor(file);
        } else {
            if (selectDir) return;
            setResult(RESULT_OK, new Intent().setData(Uri.fromFile(file)));
            finish();
        }
    }

    @Override
    public void onItemLongClick(File file) {
        if (file.isDirectory()) return;
        if (isTextFile(file)) {
            openEditor(file);
        }
    }

    @Override
    public void onCurrentDirClick(File dir) {
        if (dir == null) return;
        setResult(RESULT_OK, new Intent().setData(Uri.fromFile(dir)));
        finish();
    }

    // ==================== 文本编辑器 ====================
    private void openEditor(File file) {
        if (editorContainer == null) return;
        if (file.length() > 5 * 1024 * 1024) {
            Toast.makeText(this, "文件过大（超过5MB）", Toast.LENGTH_SHORT).show();
            return;
        }
        editingFile = file;
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) content.append(line).append("\n");
        } catch (IOException e) {
            Toast.makeText(this, "读取文件失败", Toast.LENGTH_SHORT).show();
            return;
        }
        editorText.setText(content.toString());
        editorTitle.setText("编辑: " + file.getName());
        if (mBinding.pathBar != null) mBinding.pathBar.setVisibility(View.GONE);
        mBinding.progressLayout.setVisibility(View.GONE);
        editorContainer.setVisibility(View.VISIBLE);
        editorText.requestFocus();
        initTtsEngine();
    }

    private void closeEditor() {
        stopTts();
        editorContainer.setVisibility(View.GONE);
        if (mBinding.pathBar != null) mBinding.pathBar.setVisibility(View.VISIBLE);
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        editingFile = null;
    }

    private void saveEditor() {
        if (editingFile == null || editorText == null) return;
        try (FileWriter writer = new FileWriter(editingFile)) {
            writer.write(editorText.getText().toString());
            writer.flush();
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            closeEditor();
            update(dir);
        } catch (IOException e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== TTS 语音朗读 ====================
    private void initTtsEngine() {
        if (ttsEngine != null) return;
        ttsEngine = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = ttsEngine.setLanguage(Locale.getDefault());
                ttsInitialized = (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED);
            }
        });
        ttsEngine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) {
                runOnUiThread(() -> { isSpeaking = false; updateTtsIcon(); });
            }
            @Override public void onError(String id, int code) {
                runOnUiThread(() -> { isSpeaking = false; updateTtsIcon(); });
            }
            @Override public void onError(String id) {}
        });
    }

    private void toggleTts() {
        if (!ttsInitialized || ttsEngine == null) {
            Toast.makeText(this, "语音引擎未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isSpeaking) { stopTts(); } else { startTts(); }
    }

    private void startTts() {
        String fullText = editorText.getText().toString();
        if (TextUtils.isEmpty(fullText.trim())) {
            Toast.makeText(this, "没有可朗读的文本", Toast.LENGTH_SHORT).show();
            return;
        }
        int selStart = editorText.getSelectionStart();
        int selEnd = editorText.getSelectionEnd();
        String text;
        boolean hasSelection = selStart >= 0 && selEnd >= 0 && selStart != selEnd;
        if (hasSelection) {
            // 有选中文本 → 朗读选中部分
            text = fullText.substring(Math.min(selStart, selEnd), Math.max(selStart, selEnd)).trim();
        } else if (selStart > 0) {
            // 无选中但光标不在开头 → 从光标位置朗读到末尾
            text = fullText.substring(selStart).trim();
        } else {
            // 无选中且光标在开头 → 朗读全文
            text = fullText.trim();
        }
        if (TextUtils.isEmpty(text)) {
            Toast.makeText(this, "没有可朗读的文本", Toast.LENGTH_SHORT).show();
            return;
        }
        isSpeaking = true;
        updateTtsIcon();
        int maxLen = 4000;
        if (text.length() <= maxLen) {
            ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_1");
        } else {
            ttsEngine.speak(text.substring(0, maxLen), TextToSpeech.QUEUE_FLUSH, null, "tts_0");
            int start = maxLen, idx = 1;
            while (start < text.length()) {
                int end = Math.min(start + maxLen, text.length());
                ttsEngine.speak(text.substring(start, end), TextToSpeech.QUEUE_ADD, null, "tts_" + idx);
                start = end;
                idx++;
            }
        }
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

    private boolean isTextFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".xml")
                || name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js")
                || name.endsWith(".py") || name.endsWith(".sh") || name.endsWith(".md")
                || name.endsWith(".log") || name.endsWith(".csv") || name.endsWith(".yml")
                || name.endsWith(".yaml") || name.endsWith(".ini") || name.endsWith(".cfg")
                || name.endsWith(".conf") || name.endsWith(".properties")
                || name.endsWith(".m3u") || name.endsWith(".m3u8")
                || name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".c")
                || name.endsWith(".cpp") || name.endsWith(".h") || name.endsWith(".rs");
    }

    @Override
    protected void onBackInvoked() {
        if (editorContainer != null && editorContainer.getVisibility() == View.VISIBLE) {
            closeEditor();
        } else if (isRoot()) {
            super.onBackInvoked();
        } else {
            update(dir.getParentFile());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTts();
        if (ttsEngine != null) {
            ttsEngine.stop();
            ttsEngine.shutdown();
            ttsEngine = null;
        }
    }


}
