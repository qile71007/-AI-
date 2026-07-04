package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.AdapterConfigBinding;

import java.util.ArrayList;
import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private final OnClickListener listener;
    private List<Config> mItems;
    private List<Config> mFilteredItems;
    private boolean readOnly;
    private int selectedPosition = -1;

    public interface OnClickListener {
        void onTextClick(Config item);
        void onDeleteClick(Config item);
    }

    public ConfigAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mFilteredItems = new ArrayList<>();
    }

    public ConfigAdapter readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public ConfigAdapter addAll(int type) {
        mItems = Config.getAll(type);
        if (!mItems.isEmpty() && !readOnly) mItems.remove(0);
        mFilteredItems.clear();
        mFilteredItems.addAll(mItems);
        return this;
    }

    // ==================== 搜索过滤（已修复 null 问题） ====================
    public void filter(String keyword) {
        mFilteredItems.clear();
        if (TextUtils.isEmpty(keyword)) {
            mFilteredItems.addAll(mItems);
        } else {
            String lower = keyword.toLowerCase();
            for (Config item : mItems) {
                // 安全获取字段，避免 null
                String name = item.getName();
                String url = item.getUrl();
                String desc = item.getDesc();
                
                boolean match = false;
                if (name != null && name.toLowerCase().contains(lower)) match = true;
                if (!match && url != null && url.toLowerCase().contains(lower)) match = true;
                if (!match && desc != null && desc.toLowerCase().contains(lower)) match = true;
                
                if (match) {
                    mFilteredItems.add(item);
                }
            }
        }
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public List<Config> getDisplayItems() {
        return mFilteredItems;
    }

    public int findPosition(Config target) {
        if (target == null) return -1;
        String targetUrl = target.getUrl();
        if (TextUtils.isEmpty(targetUrl)) return -1;
        for (int i = 0; i < mFilteredItems.size(); i++) {
            Config item = mFilteredItems.get(i);
            if (item != null && targetUrl.equals(item.getUrl())) {
                return i;
            }
        }
        return -1;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public Config getSelectedItem() {
        if (selectedPosition >= 0 && selectedPosition < mFilteredItems.size()) {
            return mFilteredItems.get(selectedPosition);
        }
        return null;
    }

    public int remove(Config item) {
        int position = mFilteredItems.indexOf(item);
        if (position == -1) return -1;
        item.delete();
        mFilteredItems.remove(position);
        mItems.remove(item);
        notifyItemRemoved(position);
        return getItemCount();
    }

    @Override
    public int getItemCount() {
        return mFilteredItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config item = mFilteredItems.get(position);
        holder.binding.text.setText(item.getDesc());

        // 高亮选中的配置
        boolean isSelected = (position == selectedPosition);
        holder.binding.text.setSelected(isSelected);
        holder.binding.text.setBackgroundResource(isSelected ? R.drawable.shape_item_selected : R.drawable.shape_item_normal);

        holder.binding.text.setOnClickListener(v -> {
            setSelectedPosition(position);
            if (listener != null) listener.onTextClick(item);
        });

        holder.binding.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        holder.binding.delete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(item);
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterConfigBinding binding;

        ViewHolder(@NonNull AdapterConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
