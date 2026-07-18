package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.AdapterConfigBinding;

import java.util.ArrayList;
import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private final OnClickListener listener;
    private List<Config> mItems;
    private List<Config> mFiltered;
    private String mKeyword = "";
    private boolean readOnly;
    private boolean showSecret = false;
    private String unlockedKeyword = null;

    public ConfigAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onTextClick(Config item);
        void onDeleteClick(Config item);
    }

    public ConfigAdapter readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public ConfigAdapter addAll(int type) {
        return addAll(type, null);
    }

    public ConfigAdapter addAll(int type, Config current) {
        mItems = Config.getAll(type);
        String currentUrl = current == null ? null : current.getUrl();
        if (!readOnly && !TextUtils.isEmpty(currentUrl)) {
            mItems.removeIf(item -> TextUtils.equals(item.getUrl(), currentUrl));
        }
        applyFilter();
        return this;
    }

    public void setShowSecret(boolean showSecret) {
        this.showSecret = showSecret;
        this.unlockedKeyword = null;
        applyFilter();
    }

    public void setUnlockedKeyword(String keyword) {
        this.unlockedKeyword = keyword;
        this.showSecret = false;
        applyFilter();
    }

    public boolean unlockByKeyword(String keyword) {
        if (TextUtils.isEmpty(keyword) || mItems == null) return false;
        for (Config item : mItems) {
            if (item.isSecret() && keyword.equals(item.getUnlockKeyword())) {
                setUnlockedKeyword(keyword);
                return true;
            }
        }
        return false;
    }

    public int remove(Config item) {
        int position = mFiltered.indexOf(item);
        int rawPosition = mItems.indexOf(item);
        if (rawPosition == -1) return -1;
        item.delete();
        mItems.remove(rawPosition);
        if (position != -1) {
            mFiltered.remove(position);
            notifyItemRemoved(position);
        }
        return getItemCount();
    }

    public void filter(String keyword) {
        this.mKeyword = keyword == null ? "" : keyword.trim();
        applyFilter();
    }

    private void applyFilter() {
        if (mItems == null) {
            mFiltered = new ArrayList<>();
        } else {
            String lower = mKeyword.toLowerCase();
            mFiltered = new ArrayList<>();
            for (Config item : mItems) {
                if (item.isSecret()) {
                    if (!showSecret && !keywordMatches(item)) {
                        continue;
                    }
                    if (keywordMatches(item)) {
                        mFiltered.add(item);
                        continue; // 已解锁的私密配置跳过文本过滤
                    }
                }
                String desc = item.getDesc();
                String url = item.getUrl();
                if (TextUtils.isEmpty(lower) ||
                        (desc != null && desc.toLowerCase().contains(lower)) ||
                        (url != null && url.toLowerCase().contains(lower))) {
                    mFiltered.add(item);
                }
            }
        }
        notifyDataSetChanged();
    }

    private boolean keywordMatches(Config item) {
        if (unlockedKeyword == null) return false;
        return unlockedKeyword.equals(item.getUnlockKeyword());
    }

    @Override
    public int getItemCount() {
        return mFiltered == null ? 0 : mFiltered.size();
    }

    public int getAllItemCount() {
        return mItems == null ? 0 : mItems.size();
    }

    public Config getItem(int position) {
        if (mFiltered == null || position < 0 || position >= mFiltered.size()) return null;
        return mFiltered.get(position);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config item = mFiltered.get(position);
        String displayName = item.isSecret() ? "🔒 " + item.getDesc() : item.getDesc();
        holder.binding.text.setText(displayName);
        holder.binding.text.setOnClickListener(v -> listener.onTextClick(item));
        holder.binding.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        holder.binding.delete.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterConfigBinding binding;
        ViewHolder(@NonNull AdapterConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
                              }
