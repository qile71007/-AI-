package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterFileBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<File> mItems;
    private boolean selectDir;
    private File dir;

    public FileAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(File file);

        void onCurrentDirClick(File dir);
    }

    public void addAll(File dir, List<File> items, boolean selectDir) {
        this.dir = dir;
        this.selectDir = selectDir;
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void addAll(List<File> items) {
        addAll(null, items, false);
    }

    @Override
    public int getItemCount() {
        int count = mItems.size();
        return selectDir ? count + 1 : count;
    }

    @nNonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterFileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int adjustedPosition = position;
        if (selectDir) {
            if (position == 0) {
                holder.binding.name.setText(R.string.lut_select_current_dir);
                holder.binding.getRoot().setOnClickListener(v -> listener.onCurrentDirClick(dir));
                holder.binding.image.setImageResource(R.drawable.ic_folder);
                return;
            }
            adjustedPosition = position - 1;
        }
        File file = mItems.get(adjustedPosition);
        holder.binding.name.setText(file.getName());
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(file));
        holder.binding.image.setImageResource(file.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterFileBinding binding;

        ViewHolder(@NonNull AdapterFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
