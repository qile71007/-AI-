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
        mBinding.btnBackDir.setEnabled(!mBackStack.isEmpty());
    }

    private void goBack() {
        if (mBackStack.isEmpty()) return;
        loadDirectory(mBackStack.pop());
    }

    private void goForward() {
        if (mCurrentDir == null || mCurrentDir.getParentFile() == null) return;
        mBackStack.push(mCurrentDir);
        loadDirectory(mCurrentDir.getParentFile());
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
                    mBackStack.push(mCurrentDir);
                    loadDirectory(file);
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