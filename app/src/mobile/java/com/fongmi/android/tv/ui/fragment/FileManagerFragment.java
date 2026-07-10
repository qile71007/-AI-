package com.fongmi.android.tv.ui.fragment;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileManagerFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvPath;
    private ImageButton btnBackDir, btnNewFolder;
    private FileAdapter adapter;
    private File currentDir;
    private List<File> fileList = new ArrayList<>();
    private File copiedFile = null;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    isGranted -> {
                        if (isGranted) {
                            loadDirectory(Environment.getExternalStorageDirectory());
                        } else {
                            Toast.makeText(getContext(), "需要存储权限才能访问文件", Toast.LENGTH_SHORT).show();
                        }
                    });

    private final ActivityResultLauncher<Intent> manageStorageLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            if (Environment.isExternalStorageManager()) {
                                loadDirectory(Environment.getExternalStorageDirectory());
                            } else {
                                Toast.makeText(getContext(), "需要授予「所有文件访问权限」", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });

    public static FileManagerFragment newInstance() {
        return new FileManagerFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_file_manager, container, false);
        recyclerView = view.findViewById(R.id.recyclerView);
        tvPath = view.findViewById(R.id.tv_path);
        btnBackDir = view.findViewById(R.id.btn_back_dir);
        btnNewFolder = view.findViewById(R.id.btn_new_folder);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new FileAdapter();
        recyclerView.setAdapter(adapter);

        btnBackDir.setOnClickListener(v -> goBack());
        btnNewFolder.setOnClickListener(v -> newFolder());

        checkPermissionsAndLoad();
        return view;
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                manageStorageLauncher.launch(intent);
                return;
            } else {
                loadDirectory(Environment.getExternalStorageDirectory());
            }
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                loadDirectory(Environment.getExternalStorageDirectory());
            }
        }
    }

    private void loadDirectory(File dir) {
        if (dir == null || !dir.exists()) {
            Toast.makeText(getContext(), "目录不存在", Toast.LENGTH_SHORT).show();
            return;
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
            Collections.sort(fileList, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    if (o1.isDirectory() && !o2.isDirectory()) return -1;
                    if (!o1.isDirectory() && o2.isDirectory()) return 1;
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });
        }
        adapter.notifyDataSetChanged();
    }

    private void goBack() {
        if (currentDir != null && currentDir.getParentFile() != null) {
            loadDirectory(currentDir.getParentFile());
        } else {
            Toast.makeText(getContext(), "已是根目录", Toast.LENGTH_SHORT).show();
        }
    }

    // 供 HomeActivity 调用处理返回键
    public boolean onBackPressed() {
        if (currentDir != null && currentDir.getParentFile() != null) {
            goBack();
            return true;
        }
        return false;
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
                // 兼容所有 API 版本的文件复制
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
        if (!file.canRead()) {
            Toast.makeText(getContext(), "无法读取文件", Toast.LENGTH_SHORT).show();
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

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("编辑文件: " + file.getName());
        final EditText editText = new EditText(getContext());
        editText.setMinLines(10);
        editText.setMaxLines(20);
        editText.setHorizontalScrollBarEnabled(true);
        editText.setText(content.toString());
        editText.setSelection(0);
        builder.setView(editText);
        builder.setPositiveButton("保存", (dialog, which) -> {
            String newContent = editText.getText().toString();
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(newContent);
                Toast.makeText(getContext(), "保存成功", Toast.LENGTH_SHORT).show();
                loadDirectory(currentDir);
            } catch (IOException e) {
                Toast.makeText(getContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // ---------- Adapter ----------
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
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".txt") || name.endsWith(".java") || name.endsWith(".xml") ||
                            name.endsWith(".json") || name.endsWith(".log") || name.endsWith(".md") ||
                            name.endsWith(".py") || name.endsWith(".js") || name.endsWith(".html") ||
                            name.endsWith(".css") || name.endsWith(".sh") || name.endsWith(".properties")) {
                        editTextFile(file);
                    } else {
                        Toast.makeText(getContext(), "不支持编辑此类型文件", Toast.LENGTH_SHORT).show();
                    }
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                showFileOptions(file);
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
                tvName = itemView.findViewById(R.id.tv_name);
                tvInfo = itemView.findViewById(R.id.tv_info);
                ivIcon = itemView.findViewById(R.id.iv_icon);
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
        String[] items;
        if (file.isDirectory()) {
            items = new String[]{"删除", "重命名", "复制", "剪切", "粘贴"};
        } else {
            items = new String[]{"删除", "重命名", "复制", "剪切", "粘贴", "编辑"};
        }
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
                    if (!file.isDirectory()) editTextFile(file);
                    break;
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }
}