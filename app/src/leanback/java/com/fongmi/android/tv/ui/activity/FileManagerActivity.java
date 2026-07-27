package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFileManagerBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class FileManagerActivity extends BaseActivity {

    private ActivityFileManagerBinding mBinding;
    private FileAdapter mAdapter;
    private File mCurrentDir;
    private final List<File> mFiles = new ArrayList<>();
    private final Stack<File> mBackStack = new Stack<>();
    private File mEditingFile = null;

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
        mBinding.btnNewFolder.setOnClickListener(v -> createNewFolder());
        mBinding.btnEdit.setOnClickListener(v -> toggleEditMode());
        mBinding.btnPackage.setOnClickListener(v -> Toast.makeText(this, "打包功能开发中", Toast.LENGTH_SHORT).show());
        mBinding.btnCloseEditor.setOnClickListener(v -> closeEditor());
        mBinding.btnSaveEditor.setOnClickListener(v -> saveCurrentFile());
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
        mBinding.btnBackDir.setEnabled(!mBackStack.isEmpty());
        updateEditButton();
    }

    private void goBack() {
        if (mBackStack.isEmpty()) return;
        closeEditor();
        loadDirectory(mBackStack.pop());
    }

    private void goForward() {
        if (mCurrentDir == null || mCurrentDir.getParentFile() == null) return;
        mBackStack.push(mCurrentDir);
        loadDirectory(mCurrentDir.getParentFile());
    }

    private void createNewFolder() {
        if (mCurrentDir == null) return;
        File newDir = new File(mCurrentDir, "new_folder_" + System.currentTimeMillis());
        if (newDir.mkdirs()) {
            loadDirectory(mCurrentDir);
            Toast.makeText(this, "文件夹已创建", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "创建失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleEditMode() {
        if (mEditingFile != null) {
            closeEditor();
            return;
        }
        for (File f : mFiles) {
            if (f.isFile() && isEditableFile(f.getName())) {
                openEditor(f);
                return;
            }
        }
        Toast.makeText(this, "当前目录无可编辑文件", Toast.LENGTH_SHORT).show();
    }

    private boolean isEditableFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".json") || lower.endsWith(".txt") || lower.endsWith(".xml")
                || lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.endsWith(".py")
                || lower.endsWith(".js") || lower.endsWith(".css") || lower.endsWith(".html")
                || lower.endsWith(".conf") || lower.endsWith(".cfg") || lower.endsWith(".ini")
                || lower.endsWith(".md") || lower.endsWith(".csv") || lower.endsWith(".properties")
                || lower.endsWith(".java") || lower.endsWith(".kt");
    }

    private void openEditor(File file) {
        mEditingFile = file;
        mBinding.fullscreenEditorContainer.setVisibility(View.VISIBLE);
        mBinding.fullscreenTitle.setText(file.getName());
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int) Math.min(file.length(), 1024 * 1024)];
            int len = fis.read(data);
            fis.close();
            mBinding.fullscreenEditor.setText(new String(data, 0, len));
        } catch (Exception e) {
            mBinding.fullscreenEditor.setText("无法读取文件: " + e.getMessage());
        }
        mBinding.recyclerView.setVisibility(View.GONE);
        mBinding.toolbar.setVisibility(View.GONE);
        mBinding.buttonContainer.setVisibility(View.GONE);
    }

    private void closeEditor() {
        mEditingFile = null;
        mBinding.fullscreenEditorContainer.setVisibility(View.GONE);
        mBinding.recyclerView.setVisibility(View.VISIBLE);
        mBinding.toolbar.setVisibility(View.VISIBLE);
        mBinding.buttonContainer.setVisibility(View.VISIBLE);
    }

    private void saveCurrentFile() {
        if (mEditingFile == null) return;
        try {
            FileWriter fw = new FileWriter(mEditingFile);
            fw.write(mBinding.fullscreenEditor.getText().toString());
            fw.close();
            Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
            closeEditor();
            loadDirectory(mCurrentDir);
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEditButton() {
        boolean hasEditable = false;
        for (File f : mFiles) {
            if (f.isFile() && isEditableFile(f.getName())) { hasEditable = true; break; }
        }
        mBinding.btnEdit.setVisibility(hasEditable ? View.VISIBLE : View.GONE);
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
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
                    closeEditor();
                    mBackStack.push(mCurrentDir);
                    loadDirectory(file);
                } else if (isEditableFile(file.getName())) {
                    openEditor(file);
                } else {
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
        @Override public int getItemCount() { return mFiles.size(); }
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView textView;
            ViewHolder(TextView tv) { super(tv); textView = tv; }
        }
    }
}
